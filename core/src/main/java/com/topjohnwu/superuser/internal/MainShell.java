/*
 * Copyright 2024 John "topjohnwu" Wu
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

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static com.topjohnwu.superuser.Shell.GetShellCallback;

import androidx.annotation.GuardedBy;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.concurrent.Executor;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class MainShell {

    @GuardedBy("self")
    private static final ShellImpl[] mainShell = new ShellImpl[1];

    @GuardedBy("class")
    private static boolean isInitMain;
    @GuardedBy("class")
    private static BuilderImpl mainBuilder;

    private MainShell() {}

    public static synchronized ShellImpl get() {
        ShellImpl shell = getCached();
        if (shell == null) {
            if (isInitMain) {
                throw new NoShellException("The main shell died during initialization");
            }
            isInitMain = true;
            if (mainBuilder == null)
                mainBuilder = new BuilderImpl();
            shell = mainBuilder.build();
            isInitMain = false;
        }
        return shell;
    }

    private static void returnShell(Shell s, Executor e, GetShellCallback cb) {
        if (e == null)
            cb.onShell(s);
        else
            e.execute(() -> cb.onShell(s));
    }

    public static void get(Executor executor, GetShellCallback callback) {
        Shell shell = getCached();
        if (shell != null) {
            returnShell(shell, executor, callback);
        } else {
            // Else we get shell in worker thread and call the callback when we get a Shell
            EXECUTOR.execute(() -> {
                try {
                    returnShell(get(), executor, callback);
                } catch (NoShellException e) {
                    Utils.ex(e);
                }
            });
        }
    }

    public static ShellImpl getCached() {
        synchronized (mainShell) {
            ShellImpl s = mainShell[0];
            if (s != null && s.getStatus() < 0) {
                s = null;
                mainShell[0] = null;
            }
            return s;
        }
    }

    static synchronized void setCached(ShellImpl shell) {
        if (isInitMain) {
            synchronized (mainShell) {
                mainShell[0] = shell;
            }
        }
    }

    public static synchronized void setBuilder(Shell.Builder builder) {
        if (isInitMain || getCached() != null) {
            throw new IllegalStateException("The main shell was already created");
        }
        mainBuilder = (BuilderImpl) builder;
    }

    public static Shell.Job newJob(InputStream in) {
        return new PendingJob().add(in);
    }

    public static Shell.Job newJob(String... cmds) {
        return new PendingJob().add(cmds);
    }
}
