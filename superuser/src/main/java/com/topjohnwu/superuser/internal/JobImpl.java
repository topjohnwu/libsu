/*
 * Copyright 2018 John "topjohnwu" Wu
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

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class JobImpl extends Shell.Job {

    private static final String TAG = "JOBIMPL";

    private List<String> out, err;
    private boolean redirect = false;

    ShellImpl.OutputGobblingTask task;
    Shell.ResultCallback cb;

    JobImpl() {}

    JobImpl(ShellImpl.OutputGobblingTask t) {
        task = t;
    }

    @Override
    public Shell.Result exec() {
        InternalUtils.log(TAG, "exec");
        if (out instanceof NOPList)
            out = new ArrayList<>();
        ResultImpl result = new ResultImpl();
        result.out = out;
        result.err = redirect ? out : err;
        task.setResult(result);
        try {
            task.exec();
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
            return new ResultImpl();
        }
        if (redirect)
            result.err = null;
        return result;
    }

    @Override
    public void enqueue() {
        InternalUtils.log(TAG, "enqueue");
        if (out instanceof NOPList && cb == null)
            out = null;
        task.getExecutor().execute(() -> {
            Shell.Result result = exec();
            if (cb != null)
                UiThreadHandler.run(() -> cb.onResult(result));
        });
    }

    @Override
    public Shell.Job to(List<String> stdout) {
        out = stdout;
        redirect = InternalUtils.hasFlag(Shell.FLAG_REDIRECT_STDERR);
        return this;
    }

    @Override
    public Shell.Job to(List<String> stdout, List<String> stderr) {
        out = stdout;
        err = stderr;
        redirect = false;
        return this;
    }

    @Override
    public Shell.Job onResult(Shell.ResultCallback cb) {
        this.cb = cb;
        return this;
    }
}
