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

import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.concurrent.Executor;

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static com.topjohnwu.superuser.Shell.GetShellCallback;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class MGR {

    private static boolean isInitGlobal;
    private static ShellImpl globalShell;
    private static BuilderImpl globalBuilder;

    public static synchronized ShellImpl getShell() {
        ShellImpl shell = getCachedShell();
        if (shell == null) {
            isInitGlobal = true;
            shell = getBuilder().build();
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
                    synchronized (MGR.class) {
                        isInitGlobal = true;
                        s = getBuilder().build();
                        isInitGlobal = false;
                    }
                } catch (NoShellException e) {
                    Utils.ex(e);
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

    static synchronized void setCachedShell(ShellImpl shell) {
        if (isInitGlobal) {
            // Set the global shell
            globalShell = shell;
        }
    }

    public static synchronized void setBuilder(Shell.Builder builder) {
        globalBuilder = (BuilderImpl) builder;
    }

    public static synchronized BuilderImpl getBuilder() {
        if (globalBuilder == null)
            globalBuilder = new BuilderImpl();
        return globalBuilder;
    }

    public static Shell.Job newJob(boolean su, InputStream in) {
        return new PendingJob(su).add(in);
    }

    public static Shell.Job newJob(boolean su, String... cmds) {
        return new PendingJob(su).add(cmds);
    }
}
