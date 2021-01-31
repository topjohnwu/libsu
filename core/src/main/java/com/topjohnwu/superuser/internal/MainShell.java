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

import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.concurrent.Executor;

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static com.topjohnwu.superuser.Shell.GetShellCallback;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class MainShell {

    private static boolean isInitMain;
    private static ShellImpl mainShell;
    private static BuilderImpl defaultBuilder;

    private MainShell() {}

    public static synchronized ShellImpl get() {
        ShellImpl shell = getCached();
        if (shell == null) {
            isInitMain = true;
            shell = getBuilder().build();
            isInitMain = false;
        }
        return shell;
    }

    public static void get(Executor executor, GetShellCallback callback) {
        Shell shell = getCached();
        if (shell != null) {
            if (executor == null)
                callback.onShell(shell);
            else
                executor.execute(() -> callback.onShell(shell));
        } else {
            // Else we get shell in worker thread and call the callback when we get a Shell
            EXECUTOR.execute(() -> {
                Shell s;
                try {
                    synchronized (MainShell.class) {
                        isInitMain = true;
                        s = getBuilder().build();
                        isInitMain = false;
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

    public static synchronized ShellImpl getCached() {
        if (mainShell != null && mainShell.getStatus() < 0)
            mainShell = null;
        return mainShell;
    }

    static synchronized void set(ShellImpl shell) {
        if (isInitMain)
            mainShell = shell;
    }

    public static synchronized void setBuilder(Shell.Builder builder) {
        defaultBuilder = (BuilderImpl) builder;
    }

    private static BuilderImpl getBuilder() {
        if (defaultBuilder == null)
            defaultBuilder = new BuilderImpl();
        return defaultBuilder;
    }

    public static Shell.Job newJob(boolean su, InputStream in) {
        return new PendingJob(su).add(in);
    }

    public static Shell.Job newJob(boolean su, String... cmds) {
        return new PendingJob(su).add(cmds);
    }
}
