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

import android.support.annotation.Nullable;
import android.text.TextUtils;

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
}
