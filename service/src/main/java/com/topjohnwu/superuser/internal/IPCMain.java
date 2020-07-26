/*
 * Copyright 2020 John "topjohnwu" Wu
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
import android.content.Context;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Trampoline to start a root service.
 * <p>
 * This is the only class included in main.jar as raw resources.
 * The client code will execute this main method in a root shell.
 * <p>
 * This class will get the system context by calling into Android private APIs with reflection, and
 * uses that to create our client package context. The client context will have the full APK loaded,
 * just like it was launched in a non-root environment.
 * <p>
 * Expected command-line args:
 * args[0]: client package name
 * args[1]: class name of IPCServer (reason: name could be obfuscated in client APK)
 */
public class IPCMain {

    /**
     * These private APIs are very unlikely to change, should be relatively
     * stable across different Android versions and OEMs.
     */
    @SuppressLint("PrivateApi")
    public static Context getSystemContext() {
        try {
            synchronized (Looper.class) {
                if (Looper.getMainLooper() == null)
                    Looper.prepareMainLooper();
            }

            Class<?> atClazz = Class.forName("android.app.ActivityThread");
            Method systemMain = atClazz.getMethod("systemMain");
            Object activityThread = systemMain.invoke(null);
            Method getSystemContext = atClazz.getMethod("getSystemContext");
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        // Close STDOUT/STDERR since it belongs to the parent shell
        System.out.close();
        System.err.close();

        try {
            String packageName = args[0];
            String ipcServerClassName = args[1];

            Context systemContext = getSystemContext();
            Context context = systemContext.createPackageContext(packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

            // Use classloader from the package context to run everything
            ClassLoader cl = context.getClassLoader();
            Class<?> clz = cl.loadClass(ipcServerClassName);
            Constructor<?> con = clz.getDeclaredConstructor(Context.class);
            con.setAccessible(true);
            con.newInstance(context);

            // Shall never return
            System.exit(0);
        } catch (Exception e) {
            Log.e("IPC", "Error in IPCMain", e);
            System.exit(1);
        }
    }
}
