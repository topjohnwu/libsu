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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ShellImpl extends Shell {
    private static final String TAG = "SHELLIMPL";

    private int status;
    ExecutorService SERIAL_EXECUTOR;

    private final Process process;
    private final NoCloseOutputStream STDIN;
    private final NoCloseInputStream STDOUT;
    private final NoCloseInputStream STDERR;
    private final StreamGobbler outGobbler;
    private final StreamGobbler errGobbler;
    private final byte[] endCmd;

    private static class NoCloseInputStream extends FilterInputStream {

        NoCloseInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {}

        void close0() throws IOException {
            in.close();
        }
    }

    private static class NoCloseOutputStream extends FilterOutputStream {

        NoCloseOutputStream(@NonNull OutputStream out) {
            super((out instanceof BufferedOutputStream) ? out : new BufferedOutputStream(out));
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }

        void close0() throws IOException {
            super.close();
        }
    }

    ShellImpl(long timeout, String... cmd) throws IOException {
        status = UNKNOWN;

        InternalUtils.log(TAG, "exec " + TextUtils.join(" ", cmd));
        process = Runtime.getRuntime().exec(cmd);
        STDIN = new NoCloseOutputStream(process.getOutputStream());
        STDOUT = new NoCloseInputStream(process.getInputStream());
        STDERR = new NoCloseInputStream(process.getErrorStream());

        String uuid = UUID.randomUUID().toString();
        InternalUtils.log(TAG, "UUID: " + uuid);
        outGobbler = new StreamGobbler(uuid, true);
        errGobbler = new StreamGobbler(uuid, false);
        endCmd = String.format("__RET=$?;echo %s;echo %s >&2;echo $__RET;unset __RET\n", uuid, uuid).getBytes("UTF-8");
        SERIAL_EXECUTOR = new SerialExecutorService();

        // Shell checks might get stuck indefinitely
        Future<Void> future = SERIAL_EXECUTOR.submit(() -> {
            // Clean up potential garbage from InputStreams
            ShellUtils.cleanInputStream(STDOUT);
            ShellUtils.cleanInputStream(STDERR);

            BufferedReader br = new BufferedReader(new InputStreamReader(STDOUT));

            STDIN.write(("echo SHELL_TEST\n").getBytes("UTF-8"));
            STDIN.flush();
            String s = br.readLine();
            if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST"))
                throw new IOException("Created process is not a shell");
            status = NON_ROOT_SHELL;

            STDIN.write(("id\n").getBytes("UTF-8"));
            STDIN.flush();
            s = br.readLine();
            if (!TextUtils.isEmpty(s) && s.contains("uid=0"))
                status = ROOT_SHELL;

            if (status == ROOT_SHELL && cmd.length >= 2 && TextUtils.equals(cmd[1], "--mount-master"))
                status = ROOT_MOUNT_MASTER;

            br.close();
            return null;
        });

        try {
            future.get(timeout, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            release();
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException("Unknown ExecutionException", e);
            }
        } catch (InterruptedException|TimeoutException e) {
            SERIAL_EXECUTOR.shutdownNow();
            release();
            throw new IOException("Shell timeout", e);
        }
    }

    private void release() {
        status = UNKNOWN;
        try { STDIN.close0(); } catch (IOException ignored) {}
        try { STDERR.close0(); } catch (IOException ignored) {}
        try { STDOUT.close0(); } catch (IOException ignored) {}
        process.destroy();
    }

    @Override
    public boolean waitAndClose(long timeout, @NonNull TimeUnit unit) throws InterruptedException, IOException {
        if (status < 0)
            return true;
        SERIAL_EXECUTOR.shutdown();
        if (SERIAL_EXECUTOR.awaitTermination(timeout, unit)) {
            release();
            return true;
        } else {
            status = UNKNOWN;
            return false;
        }
    }

    @Override
    public void close() {
        if (status < 0)
            return;
        SERIAL_EXECUTOR.shutdownNow();
        release();
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public boolean isAlive() {
        // If status is unknown, it is not alive
        if (status < 0)
            return false;

        try {
            process.exitValue();
            // Process is dead, shell is not alive
            return false;
        } catch (IllegalThreadStateException e) {
            // Process is still running
            return true;
        }
    }

    @Override
    public synchronized void execTask(@NonNull Task task) throws IOException {
        if (status < 0)
            throw new ShellTerminatedException();

        ShellUtils.cleanInputStream(STDOUT);
        ShellUtils.cleanInputStream(STDERR);
        try {
            STDIN.write('\n');
            STDIN.flush();
        } catch (IOException e) {
            // Shell is dead
            release();
            throw new ShellTerminatedException();
        }

        task.run(STDIN, STDOUT, STDERR);
    }

    @NonNull
    @Override
    public Job newJob() {
        return new JobImpl(this);
    }

    Task newTask(List<InputHandler> handlers, ResultImpl res) {
        return new DefaultTask(handlers, res);
    }

    private class DefaultTask implements Task {

        private final ResultImpl res;
        private final List<InputHandler> handlers;

        DefaultTask(List<InputHandler> h, ResultImpl r) {
            handlers = h;
            res = r;
        }

        @Override
        public void run(@NonNull OutputStream stdin, @NonNull InputStream stdout, @NonNull InputStream stderr) throws IOException {
            Future<Integer> out = EXECUTOR.submit(outGobbler.set(stdout, res.out));
            Future<Integer> err = EXECUTOR.submit(errGobbler.set(stderr, res.err));
            for (InputHandler handler : handlers)
                handler.handleInput(stdin);
            stdin.write(endCmd);
            stdin.flush();
            try {
                res.code = out.get();
                err.get();
            } catch (ExecutionException | InterruptedException e) {
                throw (InterruptedIOException) new InterruptedIOException().initCause(e);
            }
        }
    }
}
