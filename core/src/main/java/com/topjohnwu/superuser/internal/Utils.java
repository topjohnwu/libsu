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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Process;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class Utils {

    private static Class<?> synchronizedCollectionClass;
    private static final String TAG = "LIBSU";

    @SuppressLint("StaticFieldLeak")
    static Context context;
    static Boolean confirmedRootState;

    public static void log(Object log) {
        log(TAG, log);
    }

    public static void log(String tag, Object log) {
        if (vLog())
            Log.d(tag, log.toString());
    }

    public static void ex(Throwable t) {
        if (vLog())
            Log.d(TAG, "", t);
    }

    public static void err(Throwable t) {
        err(TAG, t);
    }

    public static void err(String tag, Throwable t) {
        Log.d(tag, "", t);
    }

    public static boolean vLog() {
        return Shell.enableVerboseLogging;
    }

    @SuppressLint("PrivateApi")
    public static Context getContext() {
        if (context == null) {
            // Fetching ActivityThread on the main thread is no longer required on API 18+
            // See: https://cs.android.com/android/platform/frameworks/base/+/66a017b63461a22842b3678c9520f803d5ddadfc
            try {
                context = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
            } catch (Exception e) {
                // Shall never happen
                Utils.err(e);
            }
        }
        return context;
    }

    public static Context getDeContext(Context context) {
        return Build.VERSION.SDK_INT >= 24 ? context.createDeviceProtectedStorageContext() : context;
    }

    public static Context getContextImpl(Context context) {
        while (context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context;
    }

    public static boolean isSynchronized(Collection<?> collection) {
        if (synchronizedCollectionClass == null) {
            synchronizedCollectionClass =
                    Collections.synchronizedCollection(NOPList.getInstance()).getClass();
        }
        return synchronizedCollectionClass.isInstance(collection);
    }

    public static long pump(InputStream in, OutputStream out) throws IOException {
        int read;
        long total = 0;
        byte[] buf = new byte[64 * 1024];  /* 64K buffer */
        while ((read = in.read(buf)) > 0) {
            out.write(buf, 0, read);
            total += read;
        }
        return total;
    }

    static <E> Set<E> newArraySet() {
        if (Build.VERSION.SDK_INT >= 23) {
            return new ArraySet<>();
        } else {
            return new HashSet<>();
        }
    }

    public synchronized static boolean isAppGrantedRoot() {
        if (confirmedRootState != null) {
            // This confirmed root state will also be set in BuilderImpl
            // and ShellImpl when new shells are getting constructed.
            return confirmedRootState;
        }
        if (Process.myUid() == 0) {
            // The current process is a root service
            confirmedRootState = true;
            return true;
        }
        try {
            Runtime.getRuntime().exec("su --version");
            // Even if the execution worked, we don't actually know whether the app has
            // been granted root access. As a heuristic, let's return true here,
            // but do NOT set the value as a confirmed state.
            return true;
        } catch (IOException e) {
            // Cannot run program "su": error=2, No such file or directory
            confirmedRootState = false;
            return false;
        }
    }
}
