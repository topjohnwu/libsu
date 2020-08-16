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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class JobImpl extends Shell.Job {

    protected List<String> out, err;
    private List<InputHandler> handlers;
    protected ShellImpl shell;
    private boolean stderrSet = false;

    JobImpl() {
        handlers = new ArrayList<>();
    }

    JobImpl(ShellImpl s) {
        this();
        shell = s;
    }

    private ResultImpl exec0() {
        ResultImpl result = new ResultImpl();
        boolean redirect = !stderrSet && shell.redirect;
        result.out = out;
        result.err = redirect ? out : err;
        Shell.Task task = shell.newTask(handlers, result);
        try {
            shell.execTask(task);
        } catch (IOException e) {
            if (e instanceof ShellTerminatedException) {
                return ResultImpl.SHELL_ERR;
            } else {
                Utils.err(e);
                return ResultImpl.INSTANCE;
            }
        }
        if (redirect)
            result.err = null;
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
        handlers.add(InputHandler.newInstance(in));
        return this;
    }

    @NonNull
    @Override
    public Shell.Job add(@NonNull String... cmds) {
        if (cmds.length > 0)
            handlers.add(InputHandler.newInstance(cmds));
        return this;
    }

}
