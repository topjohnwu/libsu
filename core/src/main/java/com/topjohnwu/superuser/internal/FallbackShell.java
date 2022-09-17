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

import static com.topjohnwu.superuser.Shell.EXECUTOR;

import androidx.annotation.GuardedBy;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.util.concurrent.Executor;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class FallbackShell {
    @GuardedBy("class")
    private static Shell fallbackShell;

    private FallbackShell() {}

    public static synchronized Shell get() {
        Shell shell = FallbackShell.getCached();
        if (shell == null) {
            try {
                shell = MainShell.get();
            } catch (NoShellException e) {
                shell = fallbackShell = new BuilderImpl()
                        .setFlags(Shell.FLAG_NON_ROOT_SHELL).build();
            }
        }
        return shell;
    }

    private static void returnShell(Shell s, Executor e, Shell.GetShellCallback cb) {
        if (e == null)
            cb.onShell(s);
        else
            e.execute(() -> cb.onShell(s));
    }

    public static void get(Executor executor, Shell.GetShellCallback callback) {
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

    // Always prioritize MainShell with root access over fallback non-root shell
    public static synchronized Shell getCached() {
        Shell s = MainShell.getCached();
        if (s != null) return s;
        s = fallbackShell;
        if (s != null && s.getStatus() < 0) {
            fallbackShell = null;
            return null;
        }
        return s;
    }
}
