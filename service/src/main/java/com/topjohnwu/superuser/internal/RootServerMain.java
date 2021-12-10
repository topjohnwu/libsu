/*
 * Copyright 2021 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.superuser.internal;

import static com.topjohnwu.superuser.internal.RootServiceManager.ACTION_ENV;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/*
Trampoline to start a root service.

This is the only class included in main.jar as a raw asset.
The client will execute the main method in a root shell.

This class will get the system context by calling into Android private APIs with reflection, and
uses that to create our client package context. The client context will have the full APK loaded,
just like it was launched in a non-root environment.

Expected command-line args:
args[0]: client service component name
args[1]: {@link #CMDLINE_START_SERVICE} or {@link #CMDLINE_STOP_SERVICE}

Expected environment variables:
LIBSU_BROADCAST_ACTION: the action used for broadcasts
*/
class RootServerMain {

    static final String CMDLINE_STOP_SERVICE = "stop";
    static final String CMDLINE_START_SERVICE = "start";

    static final Method getService;
    static final Method addService;
    static final Method attachBaseContext;

    static {
        try {
            @SuppressLint("PrivateApi")
            Class<?> sm = Class.forName("android.os.ServiceManager");
            getService = sm.getDeclaredMethod("getService", String.class);
            addService = sm.getDeclaredMethod("addService", String.class, IBinder.class);
            attachBaseContext = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);
        } catch (Exception e) {
            // Shall not happen!
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("PrivateApi")
    static Context getSystemContext() {
        try {
            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Method systemMain = atClazz.getMethod("systemMain");
            Object activityThread = systemMain.invoke(null);
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Put "libsu" in front of the service name to prevent possible conflicts
    static String getServiceName(String pkg) {
        return "libsu-" + pkg;
    }

    public static void main(String[] args) {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close();
        System.err.close();
        if (args.length < 2)
            System.exit(0);

        Looper.prepareMainLooper();
        ComponentName name = ComponentName.unflattenFromString(args[0]);
        try {
            // Get existing daemon process
            Object binder = getService.invoke(null, getServiceName(name.getPackageName()));
            IRootServiceManager m = IRootServiceManager.Stub.asInterface((IBinder) binder);

            if (args[1].equals(CMDLINE_STOP_SERVICE)) {
                if (m != null) {
                    try {
                        m.setAction(System.getenv(ACTION_ENV));
                        m.stop(name);
                    } catch (RemoteException ignored) {}
                }
                System.exit(0);
            }

            if (m != null) {
                try {
                    m.setAction(System.getenv(ACTION_ENV));
                    m.broadcast();
                    // Terminate process if broadcast went through
                    System.exit(0);
                } catch (RemoteException ignored) {
                    // Daemon process dead, continue
                }
            }

            Context systemContext = getSystemContext();
            Context context = systemContext.createPackageContext(name.getPackageName(),
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

            // Use classloader from the package context to run everything
            ClassLoader cl = context.getClassLoader();
            Class<?> clz = cl.loadClass(name.getClassName());
            Constructor<?> ctor = clz.getDeclaredConstructor();
            ctor.setAccessible(true);
            attachBaseContext.invoke(ctor.newInstance(), context);

            // Main thread event loop
            Looper.loop();

            // Shall never return
            System.exit(0);
        } catch (Exception e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }
    }
}
