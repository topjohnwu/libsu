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

package com.topjohnwu.superuser.internal;

import static com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER;
import static com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.lang.reflect.Constructor;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class BuilderImpl extends Shell.Builder {
    private static final String TAG = "BUILDER";

    long timeout = 20;
    private int flags = 0;
    private Shell.Initializer[] initializers;

    boolean hasFlags(int mask) {
        return (flags & mask) == mask;
    }

    @NonNull
    @Override
    public Shell.Builder setFlags(int f) {
        flags = f;
        return this;
    }

    @NonNull
    @Override
    public Shell.Builder setTimeout(long t) {
        timeout = t;
        return this;
    }

    public void setInitializersImpl(Class<? extends Shell.Initializer>[] clz) {
        initializers = new Shell.Initializer[clz.length];
        for (int i = 0; i < clz.length; ++i) {
            try {
                Constructor<? extends Shell.Initializer> c = clz[i].getDeclaredConstructor();
                c.setAccessible(true);
                initializers[i] = c.newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                Utils.err(e);
            }
        }
    }

    @NonNull
    @Override
    public ShellImpl build() {
        ShellImpl shell = null;

        // Root mount master
        if (!hasFlags(FLAG_NON_ROOT_SHELL) && hasFlags(FLAG_MOUNT_MASTER)) {
            try {
                shell = build("su", "--mount-master");
                if (!shell.isRoot())
                    shell = null;
            } catch (NoShellException ignore) {}
        }

        // Normal root shell
        if (shell == null && !hasFlags(FLAG_NON_ROOT_SHELL)) {
            try {
                shell = build("su");
                if (!shell.isRoot()) {
                    shell = null;
                }
            } catch (NoShellException ignore) {}
        }

        // Try normal non-root shell
        if (shell == null) {
            if (!hasFlags(FLAG_NON_ROOT_SHELL)) {
                Utils.setConfirmedRootState(false);
            }
            shell = build("sh");
        }

        return shell;
    }

    @NonNull
    @Override
    public ShellImpl build(String... commands) {
        try {
            Utils.log(TAG, "exec " + TextUtils.join(" ", commands));
            Process process = Runtime.getRuntime().exec(commands);
            return build(process);
        } catch (IOException e) {
            Utils.ex(e);
            throw new NoShellException("Unable to create a shell!", e);
        }
    }

    @NonNull
    @Override
    public ShellImpl build(Process process) {
        ShellImpl shell;
        try {
            shell = new ShellImpl(this, process);
        } catch (IOException e) {
            Utils.ex(e);
            throw new NoShellException("Unable to create a shell!", e);
        }
        MainShell.setCached(shell);
        if (initializers != null) {
            Context ctx = Utils.getContext();
            for (Shell.Initializer init : initializers) {
                if (init != null && !init.onInit(ctx, shell)) {
                    MainShell.setCached(null);
                    throw new NoShellException("Unable to init shell");
                }
            }
        }
        return shell;
    }
}
