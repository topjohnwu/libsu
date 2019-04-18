/*
 * Copyright 2019 John "topjohnwu" Wu
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

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

public class Env {
    private static int shellHash;
    private static Boolean blockdev;
    private static Boolean stat;
    private static Boolean wc;

    private static Shell checkShell() {
        Shell shell = Shell.getShell();
        int code = shell.hashCode();
        if (code != shellHash) {
            blockdev = null;
            stat = null;
            wc = null;
            shellHash = code;
        }
        return shell;
    }

    public static boolean blockdev() {
        Shell shell = checkShell();
        if (blockdev == null)
            blockdev = ShellUtils.fastCmdResult(shell, "command -v blockdev");
        return blockdev;
    }

    public static boolean stat() {
        Shell shell = checkShell();
        if (stat == null)
            stat = ShellUtils.fastCmdResult(shell, "command -v stat");
        return stat;
    }

    public static boolean wc() {
        Shell shell = checkShell();
        if (wc == null)
            wc = ShellUtils.fastCmdResult(shell, "command -v wc");
        return wc;
    }

}
