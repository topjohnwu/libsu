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

    PendingJob() {
        to(NOPList.getInstance());
    }

    private Shell.Result exec0(ResultHolder h) {
        ShellImpl shell;
        try {
            shell = MainShell.get();
        } catch (NoShellException e) {
            close();
            return ResultImpl.INSTANCE;
        }
        try {
            shell.execTask(this);
        } catch (IOException ignored) { /* JobTask does not throw */ }
        return h.result;
    }

    @NonNull
    @Override
    public Shell.Result exec() {
        ResultHolder h = new ResultHolder();
        callback = h;
        callbackExecutor = null;
        if (out instanceof NOPList)
            out = new ArrayList<>();

        Shell.Result r = exec0(h);
        if (r == ResultImpl.SHELL_ERR) {
            // The cached shell is terminated, try to re-run this task
            return exec0(h);
        }
        return r;
    }

    private class RetryCallback implements Shell.ResultCallback {

        private final Shell.ResultCallback base;
        private boolean retry = true;

        RetryCallback(Shell.ResultCallback b) {
            base = b;
        }

        @Override
        public void onResult(@NonNull Shell.Result out) {
            if (retry && out == ResultImpl.SHELL_ERR) {
                // The cached shell is terminated, try to re-schedule this task
                retry = false;
                submit0();
            } else if (base != null) {
                base.onResult(out);
            }
        }
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
        ResultFuture f = new ResultFuture();
        callback = new RetryCallback(f);
        callbackExecutor = null;
        if (out instanceof NOPList)
            out = new ArrayList<>();
        submit0();
        return f;
    }

    @Override
    public void submit(@Nullable Executor executor, @Nullable Shell.ResultCallback cb) {
        callbackExecutor = executor;
        callback = new RetryCallback(cb);
        if (out instanceof NOPList)
            out = (cb == null) ? null : new ArrayList<>();
        submit0();
    }
}
