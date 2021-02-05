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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.lang.reflect.Constructor;

import static com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER;
import static com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL;
import static com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR;
import static com.topjohnwu.superuser.Shell.ROOT_SHELL;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BuilderImpl extends Shell.Builder {

    boolean hasFlags(int flags) {
        return (this.flags & flags) == flags;
    }

    @NonNull
    @Override
    public ShellImpl build() {
        ShellImpl shell = null;

        // Root mount master
        if (!hasFlags(FLAG_NON_ROOT_SHELL) && hasFlags(FLAG_MOUNT_MASTER)) {
            try {
                shell = build("su", "--mount-master");
                if (shell.getStatus() != Shell.ROOT_MOUNT_MASTER)
                    shell = null;
            } catch (NoShellException ignore) {}
        }

        // Normal root shell
        if (shell == null && !hasFlags(FLAG_NON_ROOT_SHELL)) {
            try {
                shell = build("su");
                if (shell.getStatus() != ROOT_SHELL)
                    shell = null;
            } catch (NoShellException ignore) {}
        }

        // Try normal non-root shell
        if (shell == null)
            shell = build("sh");

        return shell;
    }

    @NonNull
    @Override
    public ShellImpl build(String... commands) {
        ShellImpl shell;
        try {
            shell = new ShellImpl(timeout, hasFlags(FLAG_REDIRECT_STDERR), commands);
        } catch (IOException e) {
            Utils.ex(e);
            throw new NoShellException("Unable to create a shell!", e);
        }
        MainShell.set(shell);
        if (initClasses != null) {
            Context ctx = Utils.getContext();
            for (Class<? extends Shell.Initializer> cls : initClasses) {
                Shell.Initializer init;
                try {
                    Constructor<? extends Shell.Initializer> ic = cls.getDeclaredConstructor();
                    ic.setAccessible(true);
                    init = ic.newInstance();
                } catch (Exception e) {
                    Utils.err(e);
                    continue;
                }
                if (!init.onInit(ctx, shell)) {
                    MainShell.set(null);
                    throw new NoShellException("Unable to init shell");
                }
            }
        }
        return shell;
    }
}
