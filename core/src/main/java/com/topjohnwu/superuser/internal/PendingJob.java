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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

class PendingJob extends JobTask {

    @Nullable
    private Runnable retryTask;

    PendingJob() {
        to(UNSET_LIST);
    }

    @Override
    public void shellDied() {
        if (retryTask != null) {
            Runnable r = retryTask;
            retryTask = null;
            r.run();
        } else {
            super.shellDied();
        }
    }

    private void exec0() {
        ShellImpl shell;
        try {
            shell = MainShell.get();
        } catch (NoShellException e) {
            super.shellDied();
            return;
        }
        try {
            shell.execTask(this);
        } catch (IOException ignored) { /* JobTask does not throw */ }
    }

    @NonNull
    @Override
    public Shell.Result exec() {
        retryTask = this::exec0;
        ResultHolder holder = new ResultHolder();
        callback = holder;
        callbackExecutor = null;
        exec0();
        return holder.getResult();
    }

    private void submit0() {
        MainShell.get(null, s -> {
            ShellImpl shell = (ShellImpl) s;
            shell.submitTask(this);
        });
    }

    @NonNull
    @Override
    public Future<Shell.Result> enqueue() {
        retryTask = this::submit0;
        ResultFuture future = new ResultFuture();
        callback = future;
        callbackExecutor = null;
        submit0();
        return future;
    }

    @Override
    public void submit(@Nullable Executor executor, @Nullable Shell.ResultCallback cb) {
        retryTask = this::submit0;
        callbackExecutor = executor;
        callback = cb;
        submit0();
    }
}
