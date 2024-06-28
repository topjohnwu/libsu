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
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

abstract class JobTask extends Shell.Job implements Shell.Task {

    static final List<String> UNSET_LIST = new ArrayList<>(0);

    static final String END_UUID = UUID.randomUUID().toString();
    static final int UUID_LEN = 36;
    private static final byte[] END_CMD = String
            .format("__RET=$?;echo %1$s;echo %1$s >&2;echo $__RET;unset __RET\n", END_UUID)
            .getBytes(UTF_8);

    private final List<ShellInputSource> sources = new ArrayList<>();
    @Nullable private List<String> out = null;
    @Nullable private List<String> err = UNSET_LIST;

    @Nullable protected Executor callbackExecutor;
    @Nullable protected Shell.ResultCallback callback;

    private void setResult(@NonNull ResultImpl result) {
        if (callback != null) {
            if (callbackExecutor == null)
                callback.onResult(result);
            else
                callbackExecutor.execute(() -> callback.onResult(result));
        }
    }

    private void close() {
        for (ShellInputSource src : sources)
            src.close();
    }

    @Override
    public void run(@NonNull OutputStream stdin,
                    @NonNull InputStream stdout,
                    @NonNull InputStream stderr) {
        final boolean noOut = out == UNSET_LIST;
        final boolean noErr = err == UNSET_LIST;

        List<String> outList = noOut ? (callback == null ? null : new ArrayList<>()) : out;
        List<String> errList = noErr ? (Shell.enableLegacyStderrRedirection ? outList : null) : err;

        if (outList != null && outList == errList && !Utils.isSynchronized(outList)) {
            // Synchronize the list internally only if both lists are the same and are not
            // already synchronized by the user
            List<String> list = Collections.synchronizedList(outList);
            outList = list;
            errList = list;
        }

        FutureTask<Integer> outGobbler = new FutureTask<>(new StreamGobbler.OUT(stdout, outList));
        FutureTask<Void> errGobbler = new FutureTask<>(new StreamGobbler.ERR(stderr, errList));
        EXECUTOR.execute(outGobbler);
        EXECUTOR.execute(errGobbler);

        ResultImpl result = new ResultImpl();
        try {
            for (ShellInputSource src : sources)
                src.serve(stdin);
            stdin.write(END_CMD);
            stdin.flush();

            int code = outGobbler.get();
            errGobbler.get();

            result.code = code;
            result.out = outList;
            result.err = noErr ? null : err;
        } catch (IOException | ExecutionException | InterruptedException e) {
            Utils.err(e);
        }

        close();
        setResult(result);
    }

    @Override
    public void shellDied() {
        close();
        setResult(new ResultImpl());
    }

    @NonNull
    @Override
    public Shell.Job to(List<String> stdout) {
        out = stdout;
        err = UNSET_LIST;
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
}
