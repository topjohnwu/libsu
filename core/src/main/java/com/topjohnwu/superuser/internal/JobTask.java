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

import static com.topjohnwu.superuser.Shell.EXECUTOR;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

abstract class JobTask extends Shell.Job implements Shell.Task, Closeable {

    static final String END_UUID = UUID.randomUUID().toString();
    static final int UUID_LEN = 36;
    private static final byte[] END_CMD = String
            .format("__RET=$?;echo %1$s;echo %1$s >&2;echo $__RET;unset __RET\n", END_UUID)
            .getBytes(UTF_8);

    private static final List<String> UNSET_ERR = new ArrayList<>(0);

    private final List<ShellInputSource> sources = new ArrayList<>();

    boolean redirect;

    protected List<String> out;
    protected List<String> err = UNSET_ERR;
    protected Executor callbackExecutor;
    protected Shell.ResultCallback callback;

    @Override
    public void run(@NonNull OutputStream stdin,
                    @NonNull InputStream stdout,
                    @NonNull InputStream stderr) {
        boolean noErr = err == UNSET_ERR;
        ResultImpl result = new ResultImpl();

        result.out = out;
        result.err = noErr ? null : err;
        if (noErr && redirect)
            result.err = out;

        if (result.out != null && result.out == result.err && !Utils.isSynchronized(result.out)) {
            // Synchronize the list internally only if both lists are the same and are not
            // already synchronized by the user
            List<String> list = Collections.synchronizedList(result.out);
            result.out = list;
            result.err = list;
        }

        FutureTask<Integer> outGobbler =
                new FutureTask<>(new StreamGobbler.OUT(stdout, result.out));
        FutureTask<Void> errGobbler = new FutureTask<>(new StreamGobbler.ERR(stderr, result.err));
        EXECUTOR.execute(outGobbler);
        EXECUTOR.execute(errGobbler);

        try {
            for (ShellInputSource src : sources)
                src.serve(stdin);
            stdin.write(END_CMD);
            stdin.flush();

            try {
                result.code = outGobbler.get();
                errGobbler.get();
                result.out = out;
                result.err = noErr ? null : err;
            } catch (ExecutionException | InterruptedException e) {
                throw (InterruptedIOException) new InterruptedIOException().initCause(e);
            }
        } catch (IOException e) {
            if (e instanceof ShellTerminatedException) {
                result = ResultImpl.SHELL_ERR;
            } else {
                Utils.err(e);
                result = ResultImpl.INSTANCE;
            }
        } finally {
            close();
            result.callback(callbackExecutor, callback);
        }
    }

    @NonNull
    @Override
    public Shell.Job to(List<String> output) {
        out = output;
        return this;
    }

    @NonNull
    @Override
    public Shell.Job to(List<String> stdout, List<String> stderr) {
        out = stdout;
        err = stderr;
        return this;
    }

    @NonNull
    @Override
    public Shell.Job add(@NonNull InputStream in) {
        if (in != null)
            sources.add(new InputStreamSource(in));
        return this;
    }

    @NonNull
    @Override
    public Shell.Job add(@NonNull String... cmds) {
        if (cmds != null && cmds.length > 0)
            sources.add(new CommandSource(cmds));
        return this;
    }

    @Override
    public void close() {
        for (ShellInputSource src : sources)
            src.close();
    }
}
