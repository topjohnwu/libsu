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

import static com.topjohnwu.superuser.internal.RootServiceManager.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * All hidden Android framework APIs used here are very stable.
 * <p>
 * These methods should only be accessed in the root process, since under normal circumstances
 * accessing these internal APIs through reflection will be blocked.
 */
class HiddenAPIs {

    @SuppressLint("StaticFieldLeak")
    private static Context systemContext;

    // Set this flag to silence AMS's complaints. Only exist on Android 8.0+
    public static final int FLAG_RECEIVER_FROM_SHELL =
            Build.VERSION.SDK_INT >= 26 ? 0x00400000 : 0;

    static synchronized Context getSystemContext() {
        if (systemContext == null)
            systemContext = RootServerMain.getSystemContext();
        return systemContext;
    }

    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    static void setAppName(String name) {
        try {
            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            Method m = ddm.getDeclaredMethod("setAppName", String.class, int.class);
            m.invoke(null, name, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static IBinder getService(String name) {
        try {
            return (IBinder) RootServerMain.getService.invoke(null, name);
        } catch (Exception e) {
            Utils.err(TAG, e);
            return null;
        }
    }

    static void addService(String name, IBinder service) {
        try {
            RootServerMain.addService.invoke(null, name, service);
        } catch (Exception e) {
            Utils.err(TAG, e);
        }
    }
}
