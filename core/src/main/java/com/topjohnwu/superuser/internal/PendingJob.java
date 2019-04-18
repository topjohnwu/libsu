/*
 * Copyright 2019 John "topjohnwu" Wu
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

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

class PendingJob extends JobImpl {

    private boolean isSU;

    PendingJob(boolean su) {
        isSU = su;
        to(NOPList.getInstance());
    }

    @Override
    public Shell.Result exec() {
        try {
            shell = (ShellImpl) Shell.getShell();
        } catch (NoShellException e) {
            return ResultImpl.INSTANCE;
        }
        if (isSU && !shell.isRoot())
            return ResultImpl.INSTANCE;
        return super.exec();
    }

    @Override
    public void submit(Shell.ResultCallback cb) {
        Shell.getShell(s -> {
            if (isSU && !s.isRoot() && cb != null) {
                cb.onResult(ResultImpl.INSTANCE);
                return;
            }
            shell = (ShellImpl) s;
            super.submit(cb);
        });
    }
}
