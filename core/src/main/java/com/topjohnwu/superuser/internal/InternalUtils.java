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

import android.content.Context;
import android.util.Log;

import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class InternalUtils {

    private static Method currentApplication;
    private static WeakReference<Context> weakContext;

    static {
        try {
            currentApplication = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication");
        } catch (Exception e) {
            /* Impossible */
        }
    }

    public static void log(String tag, Object log) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            Log.d(tag, log.toString());
    }

    public static void stackTrace(Throwable t) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            Log.d("LIBSU", "Internal Error", t);
    }

    public static boolean hasFlag(int flags) {
        return hasFlag(Impl.flags, flags);
    }

    public static boolean hasFlag(int base, int flags) {
        return (base & flags) == flags;
    }

    public static Context getContext() {
        if (weakContext == null || weakContext.get() == null) {
            UiThreadHandler.runAndWait(() -> {
                try {
                    weakContext = new WeakReference<>((Context) currentApplication.invoke(null));
                } catch (Exception e) {
                    weakContext = new WeakReference<>(null);
                }
            });
        }
        return weakContext.get();
    }
}
