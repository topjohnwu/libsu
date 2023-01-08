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

import static com.topjohnwu.superuser.internal.RootServiceManager.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * All hidden Android framework APIs used here are very stable.
 * <p>
 * These methods should only be accessed in the root process, since under normal circumstances
 * accessing these internal APIs through reflection will be blocked.
 */
@SuppressLint("PrivateApi,DiscouragedPrivateApi,SoonBlockedPrivateApi")
class HiddenAPIs {

    private static Method addService;
    private static Method attachBaseContext;
    private static Method setAppName;

    // Set this flag to silence AMS's complaints. Only exist on Android 8.0+
    public static final int FLAG_RECEIVER_FROM_SHELL =
            Build.VERSION.SDK_INT >= 26 ? 0x00400000 : 0;

    static {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    addService = sm.getDeclaredMethod("addService",
                            String.class, IBinder.class, boolean.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Fallback to the 2 argument version
                }
            }
            if (addService == null) {
                addService = sm.getDeclaredMethod("addService", String.class, IBinder.class);
            }

            attachBaseContext = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);

            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            setAppName = ddm.getDeclaredMethod("setAppName", String.class, int.class);
        } catch (ReflectiveOperationException e) {
            Utils.err(TAG, e);
        }
    }

    static void setAppName(String name) {
        try {
            setAppName.invoke(null, name, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void addService(String name, IBinder service) {
        try {
            if (addService.getParameterTypes().length == 4) {
                // Set dumpPriority to 0 so the service cannot be listed
                addService.invoke(null, name, service, false, 0);
            } else {
                addService.invoke(null, name, service);
            }
        } catch (ReflectiveOperationException e) {
            Utils.err(TAG, e);
        }
    }

    static void attachBaseContext(Object wrapper, Context context) {
        if (wrapper instanceof ContextWrapper) {
            try {
                attachBaseContext.invoke(wrapper, context);
            } catch (ReflectiveOperationException ignored) { /* Impossible */ }
        }
    }
}
