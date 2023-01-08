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

package com.topjohnwu.superuser;

import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Some handy utility methods that are used in {@code libsu}.
 * <p>
 * These methods are for internal use. I personally find them pretty handy, so I gathered them here.
 * However, since these are meant to be used internally, they are not stable APIs.
 * I would change them without too much consideration if needed. Also, these methods are not well
 * tested for public usage, many might not handle some edge cases correctly.
 * <strong>You have been warned!!</strong>
 */
public final class ShellUtils {

    private ShellUtils() {}

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
     * Run commands with the main shell and get a single line output.
     * @param cmds the commands.
     * @return the last line of the output of the command, empty string if no output is available.
     */
    @NonNull
    public static String fastCmd(String... cmds) {
        return fastCmd(Shell.getShell(), cmds);
    }

    /**
     * Run commands and get a single line output.
     * @param shell a shell instance.
     * @param cmds the commands.
     * @return the last line of the output of the command, empty string if no output is available.
     */
    @NonNull
    public static String fastCmd(Shell shell, String... cmds) {
        List<String> out = shell.newJob().add(cmds).to(new ArrayList<>(), null).exec().getOut();
        return isValidOutput(out) ? out.get(out.size() - 1) : "";
    }

    /**
     * Run commands with the main shell and return whether exits with 0 (success).
     * @param cmds the commands.
     * @return {@code true} if the commands succeed.
     */
    public static boolean fastCmdResult(String... cmds) {
        return fastCmdResult(Shell.getShell(), cmds);
    }

    /**
     * Run commands and return whether exits with 0 (success).
     * @param shell a shell instance.
     * @param cmds the commands.
     * @return {@code true} if the commands succeed.
     */
    public static boolean fastCmdResult(Shell shell, String... cmds) {
        return shell.newJob().add(cmds).to(null).exec().isSuccess();
    }

    /**
     * Check if current thread is main thread.
     * @return {@code true} if the current thread is the main thread.
     */
    public static boolean onMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
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

    private static final char SINGLE_QUOTE = '\'';

    /**
     * Format string to quoted and escaped string suitable for shell commands.
     * @param s the string to be formatted.
     * @return the formatted string.
     */
    public static String escapedString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append(SINGLE_QUOTE);
        int len = s.length();
        for (int i = 0; i < len; ++i) {
            char c = s.charAt(i);
            if (c == SINGLE_QUOTE) {
                sb.append("'\\''");
                continue;
            }
            sb.append(c);
        }
        sb.append(SINGLE_QUOTE);
        return sb.toString();
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
