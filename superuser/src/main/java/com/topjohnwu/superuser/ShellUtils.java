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

package com.topjohnwu.superuser;

import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class ShellUtils {

    private ShellUtils() {}

    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER_CASE = LOWER_CASE.toUpperCase();
    private static final String NUMBERS = "0123456789";
    private static final String ALPHANUM = LOWER_CASE + UPPER_CASE + NUMBERS;

    public static CharSequence genRandomAlphaNumString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            builder.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return builder;
    }

    public static boolean isValidOutput(List<String> out) {
        if (out != null && out.size() != 0) {
            // Check if all empty
            for (String s : out)
                if (!TextUtils.isEmpty(s))
                    return true;
        }
        return false;
    }

    @Nullable
    public static String fastCmd(String... commands) {
        return fastCmd(Shell.getShell(), commands);
    }

    @Nullable
    public static String fastCmd(Shell shell, String... commands) {
        ArrayList<String> out = new ArrayList<>(1);
        shell.run(out, null, commands);
        return isValidOutput(out) ? out.get(out.size() - 1) : null;
    }

    public static long pump(InputStream in, OutputStream out) throws IOException {
        int read;
        long total = 0;
        byte buffer[] = new byte[64 * 1024];  /* 64K buffer */
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            total += read;
        }
        out.flush();
        return total;
    }

    public static boolean checkSum(String alg, File file, String test) {
        // Verify checksum
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(alg);
            pump(in, new DigestOutputStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {}

                @Override
                public void write(@NonNull byte[] b, int off, int len) throws IOException {}
            }, digest));
            byte[] chksum = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : chksum) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return TextUtils.equals(sb, test);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
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

    public static long gcd(long u, long v) {
        if (u == 0) return v;
        if (v == 0) return u;

        int shift;
        for (shift = 0; ((u | v) & 1) == 0; ++shift) {
            u >>= 1;
            v >>= 1;
        }
        while ((u & 1) == 0)
            u >>= 1;
        do {
            while ((v & 1) == 0)
                v >>= 1;

            if (u > v) {
                long t = v;
                v = u;
                u = t;
            }
            v = v - u;
        } while (v != 0);

        return u << shift;
    }
}
