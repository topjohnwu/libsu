/*
 * Copyright 2018 John "topjohnwu" Wu
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

import android.os.Looper;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class InternalUtils {
    public static void log(String tag, Object log) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            Log.d(tag, log.toString());
    }

    public static void stackTrace(Throwable t) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            Log.d("LIBSU", "Internal Error", t);
    }

    public static boolean onMainThread() {
        return ((Looper.myLooper() != null) && (Looper.myLooper() == Looper.getMainLooper()));
    }

    public static void cleanInputStream(InputStream in) {
        try {
            while (in.available() != 0)
                in.skip(in.available());
        } catch (IOException ignored) {}
    }

    public static void readFully(InputStream in, byte[] b) throws IOException {
        readFully(in, b, 0, b.length);
    }

    public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    public static boolean hasFlag(int flag) {
        return hasFlag(Shell.getFlags(), flag);
    }

    public static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
