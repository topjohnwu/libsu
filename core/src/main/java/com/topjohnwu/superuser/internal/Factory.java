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

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;

public final class Factory {

    public static ShellImpl createShell(long timeout, String... cmd) throws IOException {
        return new ShellImpl(timeout, cmd);
    }

    public static Shell.Job createJob(boolean su, InputStream in) {
        return new PendingJob(su).add(in);
    }

    public static Shell.Job createJob(boolean su, String... cmds) {
        return new PendingJob(su).add(cmds);
    }
}
