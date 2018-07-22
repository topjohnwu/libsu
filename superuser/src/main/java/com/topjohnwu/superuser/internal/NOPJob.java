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

public class NOPJob extends Shell.Job {

    private Shell.ResultCallback cb;

    @Override
    public Shell.Job to(Shell.Output out) {
        return this;
    }

    @Override
    public Shell.Job onResult(Shell.ResultCallback cb) {
        this.cb = cb;
        return this;
    }

    @Override
    public Shell.Output exec() {
        return new Shell.Output(null, null);
    }

    @Override
    public void enqueue() {
        cb.onResult(exec());
    }
}
