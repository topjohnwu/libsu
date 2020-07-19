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

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;

class PendingJob extends JobImpl {

    private boolean isSU;
    private boolean retried;

    PendingJob(boolean su) {
        isSU = su;
        retried = false;
        to(NOPList.getInstance());
    }

    @NonNull
    @Override
    public Shell.Result exec() {
        try {
            shell = Impl.getShell();
        } catch (NoShellException e) {
            return ResultImpl.INSTANCE;
        }
        if (isSU && !shell.isRoot())
            return ResultImpl.INSTANCE;
        if (out instanceof NOPList)
            out = new ArrayList<>();
        Shell.Result res = super.exec();
        if (!retried && res == ResultImpl.SHELL_ERR) {
            // The cached shell is terminated, try to re-run this task
            retried = true;
            return exec();
        }
        return res;
    }

    @Override
    public void submit(Shell.ResultCallback cb) {
        Shell.getShell(s -> {
            if (isSU && !s.isRoot()) {
                if (cb != null)
                    UiThreadHandler.run(() -> cb.onResult(ResultImpl.INSTANCE));
                return;
            }
            if (out instanceof NOPList)
                out = (cb == null) ? null : new ArrayList<>();
            shell = (ShellImpl) s;
            super.submit(res -> {
                if (!retried && res == ResultImpl.SHELL_ERR) {
                    // The cached shell is terminated, try to re-schedule this task
                    retried = true;
                    submit(cb);
                } else if (cb != null) {
                    cb.onResult(res);
                }
            });
        });
    }
}
