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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

class JobImpl extends Shell.Job implements Closeable {

    protected List<String> out, err;
    private final List<ShellInputSource> sources = new ArrayList<>();
    protected ShellImpl shell;
    private boolean stderrSet = false;

    JobImpl() {}

    JobImpl(ShellImpl s) {
        shell = s;
    }

    private ResultImpl exec0() {
        boolean redirect = !stderrSet && shell.redirect;
        if (redirect)
            err = out;

        ResultImpl result = new ResultImpl();
        if (out != null && out == err && !Utils.isSynchronized(out)) {
            // Synchronize the list internally only if both lists are the same and are not
            // already synchronized by the user
            List<String> list = Collections.synchronizedList(out);
            result.out = list;
            result.err = list;
        } else {
            result.out = out;
            result.err = err;
        }
        try {
            shell.execTask(new TaskImpl(sources, result));
        } catch (IOException e) {
            if (e instanceof ShellTerminatedException) {
                return ResultImpl.SHELL_ERR;
            } else {
                Utils.err(e);
                return ResultImpl.INSTANCE;
            }
        } finally {
            close();
            result.out = out;
            result.err = redirect ? null : err;
        }
        return result;
    }

    @NonNull
    @Override
    public Shell.Result exec() {
        return exec0();
    }

    @Override
    public void submit(@Nullable Executor executor, @Nullable Shell.ResultCallback cb) {
        shell.executor.execute(() -> exec0().callback(executor, cb));
    }

    @NonNull
    @Override
    public Shell.Job to(List<String> output) {
        out = output;
        err = null;
        stderrSet = false;
        return this;
    }

    @NonNull
    @Override
    public Shell.Job to(List<String> stdout, List<String> stderr) {
        out = stdout;
        err = stderr;
        stderrSet = true;
        return this;
    }

    @NonNull
    @Override
    public Shell.Job add(@NonNull InputStream in) {
        if (in != null)
            sources.add(new InputStreamSource(in));
        return this;
    }

    @NonNull
    @Override
    public Shell.Job add(@NonNull String... cmds) {
        if (cmds != null && cmds.length > 0)
            sources.add(new CommandSource(cmds));
        return this;
    }

    @Override
    public void close() {
        for (ShellInputSource src : sources)
            src.close();
    }
}
