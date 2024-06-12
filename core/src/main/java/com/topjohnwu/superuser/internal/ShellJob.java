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

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

class ShellJob extends JobTask {

    private final ShellImpl shell;

    ShellJob(ShellImpl s) {
        shell = s;
    }

    @NonNull
    @Override
    public Shell.Result exec() {
        ResultHolder h = new ResultHolder();
        callback = h;
        callbackExecutor = null;
        try {
            shell.execTask(this);
        } catch (IOException ignored) { /* JobTask does not throw */ }
        return h.result;
    }

    @Override
    public void submit(@Nullable Executor executor, @Nullable Shell.ResultCallback cb) {
        callbackExecutor = executor;
        callback = cb;
        shell.submitTask(this);
    }

    @NonNull
    @Override
    public Future<Shell.Result> enqueue() {
        ResultFuture f = new ResultFuture();
        callback = f;
        callbackExecutor = null;
        shell.submitTask(this);
        return f;
    }
}
