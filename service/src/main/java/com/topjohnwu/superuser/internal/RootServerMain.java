/*
 * Copyright 2023 John "topjohnwu" Wu
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

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/*
Trampoline to start a root service.

This is the only class included in main.jar as a raw asset.
The client will execute the main method in a root shell.

This class will get the system context by calling into Android private APIs with reflection, and
uses that to create our client package context. The client context will have the full APK loaded,
just like it was launched in a non-root environment.

Expected command-line args:
args[0]: client service component name
args[1]: client UID
args[2]: CMDLINE_START_SERVICE, CMDLINE_START_DAEMON, or CMDLINE_STOP_SERVICE
*/
class RootServerMain extends ContextWrapper implements Callable<Object[]> {

    static final String CMDLINE_START_SERVICE = "start";
    static final String CMDLINE_START_DAEMON = "daemon";
    static final String CMDLINE_STOP_SERVICE = "stop";

    private static final Method getService;
    private static final Method attachBaseContext;

    static {
        try {
            @SuppressLint("PrivateApi")
            Class<?> sm = Class.forName("android.os.ServiceManager");
            getService = sm.getDeclaredMethod("getService", String.class);
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
        if (args.length < 3)
            System.exit(1);

        Looper.prepareMainLooper();

        try {
            new RootServerMain(args);
        } catch (Exception e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }

        // Main thread event loop
        Looper.loop();
        System.exit(1);
    }

    private final int uid;
    private final boolean isDaemon;

    @Override
    public Object[] call() {
        Object[] objs = new Object[2];
        objs[0] = uid;
        objs[1] = isDaemon;
        return objs;
    }

    @SuppressLint("DiscouragedPrivateApi")
    public RootServerMain(String[] args) throws Exception {
        super(null);

        ComponentName name = ComponentName.unflattenFromString(args[0]);
        uid = Integer.parseInt(args[1]);
        String action = args[2];
        boolean stop = false;

        switch (action) {
            case CMDLINE_STOP_SERVICE:
                stop = true;
                // fallthrough
            case CMDLINE_START_DAEMON:
                isDaemon = true;
                break;
            case CMDLINE_START_SERVICE:
                isDaemon = false;
                break;
            default:
                throw new IllegalArgumentException("Unknown action");
        }

        if (isDaemon) daemon: try {
            // Get existing daemon process
            Object binder = getService.invoke(null, getServiceName(name.getPackageName()));
            IRootServiceManager m = IRootServiceManager.Stub.asInterface((IBinder) binder);
            if (m == null)
                break daemon;

            if (stop) {
                m.stop(name, uid);
            } else {
                m.broadcast(uid);
                // Terminate process if broadcast went through without exception
                System.exit(0);
            }
        } catch (RemoteException ignored) {
        } finally {
            if (stop)
                System.exit(0);
        }

        // Calling many system APIs can crash on some LG ROMs
        // Override the system resources object to prevent crashing
        try {
            // This class only exists on LG ROMs with broken implementations
            Class.forName("com.lge.systemservice.core.integrity.IntegrityManager");
            // If control flow goes here, we need the resource hack
            Resources systemRes = Resources.getSystem();
            Resources wrapper = new ResourcesWrapper(systemRes);
            Field systemResField = Resources.class.getDeclaredField("mSystem");
            systemResField.setAccessible(true);
            systemResField.set(null, wrapper);
        } catch (ReflectiveOperationException ignored) {}

        Context systemContext = getSystemContext();
        Context context;
        int userId = uid / 100000; // UserHandler.getUserId
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        try {
            UserHandle userHandle;
            try {
                userHandle = (UserHandle) UserHandle.class
                           .getDeclaredMethod("of", int.class).invoke(null, userId);
            } catch (NoSuchMethodException e) {
                userHandle = UserHandle.class
                           .getDeclaredConstructor(int.class).newInstance(userId);
            }
            context = (Context) systemContext.getClass()
                    .getDeclaredMethod("createPackageContextAsUser",
                            String.class, int.class, UserHandle.class)
                    .invoke(systemContext, name.getPackageName(), flags, userHandle);
        } catch (Throwable e) {
            Log.w("IPC", "Failed to create package context as user: " + userId, e);
            context = systemContext.createPackageContext(name.getPackageName(), flags);
        }
        attachBaseContext(context);

        // Use classloader from the package context to run everything
        ClassLoader cl = context.getClassLoader();

        Class<?> clz = cl.loadClass(name.getClassName());
        Constructor<?> ctor = clz.getDeclaredConstructor();
        ctor.setAccessible(true);
        attachBaseContext.invoke(ctor.newInstance(), this);
    }

    static class ResourcesWrapper extends Resources {

        @SuppressWarnings("JavaReflectionMemberAccess")
        @SuppressLint("DiscouragedPrivateApi")
        public ResourcesWrapper(Resources res) throws ReflectiveOperationException {
            super(res.getAssets(), res.getDisplayMetrics(), res.getConfiguration());
            Method getImpl = Resources.class.getDeclaredMethod("getImpl");
            getImpl.setAccessible(true);
            Method setImpl = Resources.class.getDeclaredMethod("setImpl", getImpl.getReturnType());
            setImpl.setAccessible(true);
            Object impl = getImpl.invoke(res);
            setImpl.invoke(this, impl);
        }

        @Override
        public boolean getBoolean(int id) {
            try {
                return super.getBoolean(id);
            } catch (NotFoundException e) {
                return false;
            }
        }
    }
}
