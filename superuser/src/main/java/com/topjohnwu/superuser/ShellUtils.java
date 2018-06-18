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

/**
 * Some handy utility methods that are used in {@code libsu}.
 * <p>
 * These methods are for internal use. I personally find them pretty handy, so I gathered them here.
 * However, since these are meant to be used internally, they are not stable APIs.
 * I would change them without too much consideration if needed. Also, these methods are not well
 * tested for public usage, many might not handle some edge cases correctly.
 * <heavy>You have been warned!!</heavy>
 */
public final class ShellUtils {

    private ShellUtils() {}

    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER_CASE = LOWER_CASE.toUpperCase();
    private static final String NUMBERS = "0123456789";
    private static final String ALPHANUM = LOWER_CASE + UPPER_CASE + NUMBERS;

    /**
     * Generate a random string containing only alphabet and numbers.
     * @param length the length of the desired random string.
     * @return the random string.
     */
    public static CharSequence genRandomAlphaNumString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            builder.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return builder;
    }

    /**
     * Test whether the list is {@code null} or empty or all elements are empty strings.
     * @param out the output of a shell command.
     * @return {@code false} if the list is {@code null} or empty or all elements are empty strings.
     */
    public static boolean isValidOutput(List<String> out) {
        if (out != null && out.size() != 0) {
            // Check if all empty
            for (String s : out)
                if (!TextUtils.isEmpty(s))
                    return true;
        }
        return false;
    }

    /**
     * Run commands with the global shell and get a single line output.
     * @param cmds the commands.
     * @return the last line of the output of the command, {@code null} if no output is available.
     */
    @Nullable
    public static String fastCmd(String... cmds) {
        return fastCmd(Shell.getShell(), cmds);
    }

    /**
     * Run commands and get a single line output.
     * @param shell a shell instance.
     * @param cmds the commands.
     * @return the last line of the output of the command, {@code null} if no output is available.
     */
    @Nullable
    public static String fastCmd(Shell shell, String... cmds) {
        ArrayList<String> out = new ArrayList<>();
        shell.run(out, null, cmds);
        return isValidOutput(out) ? out.get(out.size() - 1) : null;
    }

    /**
     * Run a single line command with the global shell and return whether the command returns 0 (success).
     * @param cmd the single line command.
     * @return {@code true} if the command succeed.
     */
    public static boolean fastCmdResult(String cmd) {
        return fastCmdResult(Shell.getShell(), cmd);
    }

    /**
     * Run a single line command and return whether the command returns 0 (success).
     * @param shell a shell instance.
     * @param cmd the single line command.
     * @return {@code true} if the command succeed.
     */
    public static boolean fastCmdResult(Shell shell, String cmd) {
        return Boolean.parseBoolean(fastCmd(shell, cmd + " >/dev/null 2>&1 && echo true || echo false"));
    }

    /**
     * Pump all data from an {@link InputStream} to an {@link OutputStream}.
     * @param in source.
     * @param out target.
     * @return the total bytes transferred.
     * @throws IOException when any read/write operations throws an error.
     */
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

    /**
     * Check the checksum of a file using a specific algorithm and compare it with a reference.
     * @param alg the algorithm name used in {@link MessageDigest#getInstance(String)}.
     * @param file the file to be tested.
     * @param reference the reference checksum.
     * @return {@code true} if the file's checksum matches reference.
     */
    public static boolean checkSum(String alg, File file, String reference) {
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
            return TextUtils.equals(sb, reference);
        } catch (NoSuchAlgorithmException | IOException e) {
            return false;
        }
    }

    /**
     * Check if current thread is main thread.
     * @return {@code true} if the current thread is the main thread.
     */
    public static boolean onMainThread() {
        return ((Looper.myLooper() != null) && (Looper.myLooper() == Looper.getMainLooper()));
    }

    /**
     * Discard all data currently available in an {@link InputStream}.
     * @param in the {@link InputStream} to be cleaned.
     */
    public static void cleanInputStream(InputStream in) {
        try {
            while (in.available() != 0)
                in.skip(in.available());
        } catch (IOException ignored) {}
    }

    /**
     * Same as {@code readFully(in, b, 0, b.length)}
     */
    public static void readFully(InputStream in, byte[] b) throws IOException {
        readFully(in, b, 0, b.length);
    }

    /**
     * Read exactly len bytes from the {@link InputStream}.
     * @param in source.
     * @param b the byte array to store the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to be read.
     * @throws EOFException if this stream reaches the end before reading all the bytes.
     * @throws IOException if an I/O error occurs.
     */
    public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    /**
     * Get the greatest common divisor of 2 integers with binary algorithm.
     * @param u an integer.
     * @param v an integer.
     * @return the greatest common divisor.
     */
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
