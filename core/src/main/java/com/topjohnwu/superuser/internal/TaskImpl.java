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

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static com.topjohnwu.superuser.internal.Utils.UTF_8;

class TaskImpl implements Shell.Task {

    static final String END_UUID;
    static final byte[] END_CMD;
    static final int UUID_LEN = 36;

    static {
        END_UUID = UUID.randomUUID().toString();
        END_CMD = String
                .format("__RET=$?;echo %1$s;echo %1$s >&2;echo $__RET;unset __RET\n", END_UUID)
                .getBytes(UTF_8);
        // UUID_LEN = END_UUID.length();
    }

    private final List<ShellInputSource> sources;
    private final ResultImpl res;

    TaskImpl(List<ShellInputSource> sources, ResultImpl res) {
        this.sources = sources;
        this.res = res;
    }

    @Override
    public void run(@NonNull OutputStream stdin,
                    @NonNull InputStream stdout,
                    @NonNull InputStream stderr) throws IOException {

        Future<Integer> out = EXECUTOR.submit(new StreamGobbler.OUT(stdout, res.out));
        Future<Void> err = EXECUTOR.submit(new StreamGobbler.ERR(stderr, res.err));

        for (ShellInputSource src : sources)
            src.serve(stdin);
        stdin.write(END_CMD);
        stdin.flush();

        try {
            res.code = out.get();
            err.get();
        } catch (ExecutionException | InterruptedException e) {
            throw (InterruptedIOException) new InterruptedIOException().initCause(e);
        }
    }
}
