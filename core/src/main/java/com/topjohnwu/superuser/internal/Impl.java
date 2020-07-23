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

import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER;
import static com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL;
import static com.topjohnwu.superuser.Shell.GetShellCallback;
import static com.topjohnwu.superuser.Shell.ROOT_SHELL;
import static com.topjohnwu.superuser.internal.InternalUtils.hasFlag;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class Impl {

    public static int flags = 0;
    public static long timeout = 20;
    public static Class<? extends Shell.Initializer>[] initClasses = null;

    private static boolean isInitGlobal;
    private static ShellImpl globalShell;

    public static synchronized ShellImpl getShell() {
        ShellImpl shell = getCachedShell();
        if (shell == null) {
            isInitGlobal = true;
            shell = newShell();
            isInitGlobal = false;
        }
        return shell;
    }

    public static void getShell(Executor executor, GetShellCallback callback) {
        Shell shell = getCachedShell();
        if (shell != null) {
            // If cached shell exists, run synchronously
            callback.onShell(shell);
        } else {
            // Else we get shell in worker thread and call the callback when we get a Shell
            EXECUTOR.execute(() -> {
                Shell s;
                try {
                    synchronized (Impl.class) {
                        isInitGlobal = true;
                        s = newShell();
                        isInitGlobal = false;
                    }
                } catch (NoShellException e) {
                    InternalUtils.stackTrace(e);
                    return;
                }
                if (executor == null)
                    callback.onShell(s);
                else
                    executor.execute(() -> callback.onShell(s));
            });
        }
    }

    public static synchronized ShellImpl getCachedShell() {
        if (globalShell != null && globalShell.getStatus() < 0)
            globalShell = null;
        return globalShell;
    }

    public static ShellImpl newShell() {
        ShellImpl shell = null;

        // Root mount master
        if (!hasFlag(FLAG_NON_ROOT_SHELL) && hasFlag(FLAG_MOUNT_MASTER)) {
            try {
                shell = newShell("su", "--mount-master");
                if (shell.getStatus() != Shell.ROOT_MOUNT_MASTER)
                    shell = null;
            } catch (NoShellException ignore) {}
        }

        // Normal root shell
        if (shell == null && !hasFlag(FLAG_NON_ROOT_SHELL)) {
            try {
                shell = newShell("su");
                if (shell.getStatus() != ROOT_SHELL)
                    shell = null;
            } catch (NoShellException ignore) {}
        }

        // Try normal non-root shell
        if (shell == null)
            shell = newShell("sh");

        return shell;
    }

    public static ShellImpl newShell(String... commands) {
        try {
            ShellImpl shell = new ShellImpl(timeout, commands);
            try {
                Context ctx = InternalUtils.getContext();
                setCachedShell(shell);
                if (initClasses != null) {
                    for (Class<? extends Shell.Initializer> cls : initClasses) {
                        Constructor<? extends Shell.Initializer> ic = cls.getDeclaredConstructor();
                        ic.setAccessible(true);
                        Shell.Initializer init = ic.newInstance();
                        if (!init.onInit(ctx, shell)) {
                            setCachedShell(null);
                            throw new NoShellException("Unable to init shell");
                        }
                    }
                }
            } catch (Exception e) {
                if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                InternalUtils.stackTrace(e);
            }
            return shell;
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
            throw new NoShellException("Unable to create a shell!", e);
        }
    }

    private static synchronized void setCachedShell(ShellImpl shell) {
        if (isInitGlobal) {
            // Set the global shell
            globalShell = shell;
        }
    }

    public static Shell.Job newJob(boolean su, InputStream in) {
        return new PendingJob(su).add(in);
    }

    public static Shell.Job newJob(boolean su, String... cmds) {
        return new PendingJob(su).add(cmds);
    }
}
