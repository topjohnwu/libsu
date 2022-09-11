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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class Utils {

    private static Class<?> synchronizedCollectionClass;
    private static final String TAG = "LIBSU";

    // -1: uninitialized
    //  0: checked, no root
    //  1: checked, undetermined
    //  2: checked, root access
    private static int currentRootState = -1;

    @SuppressLint("StaticFieldLeak")
    static Context context;

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
                Context c = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                context = getContextImpl(c);
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

    public static boolean hasStartupAgents(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return false;
        File agents = new File(context.getCodeCacheDir(), "startup_agents");
        return agents.isDirectory();
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

    public synchronized static Boolean isAppGrantedRoot() {
        if (currentRootState < 0) {
            if (Process.myUid() == 0) {
                // The current process is a root service
                currentRootState = 2;
                return true;
            }
            // noinspection ConstantConditions
            for (String path : System.getenv("PATH").split(":")) {
                File su = new File(path, "su");
                if (su.canExecute()) {
                    // We don't actually know whether the app has been granted root access.
                    // Do NOT set the value as a confirmed state.
                    currentRootState = 1;
                    return null;
                }
            }
            currentRootState = 0;
            return false;
        }
        switch (currentRootState) {
            case 0 : return false;
            case 2 : return true;
            default: return null;
        }
    }

    synchronized static void setConfirmedRootState(boolean value) {
        currentRootState = value ? 2 : 0;
    }

    public static boolean isRootImpossible() {
        return Objects.equals(isAppGrantedRoot(), Boolean.FALSE);
    }

    public static boolean isMainShellRoot() {
        return MainShell.get().isRoot();
    }
}
