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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class ShellImpl extends ShellCompat.Impl {
    private static final String TAG = "SHELLIMPL";
    private static final String INTAG = "SHELL_IN";
    private static final int UNINT = -2;

    private int status;

    private final String token;
    private final Process process;
    private final NoCloseOutputStream STDIN;
    private final NoCloseInputStream STDOUT;
    private final NoCloseInputStream STDERR;
    private final StreamGobbler outGobbler;
    private final StreamGobbler errGobbler;
    private final ExecutorService SERIAL_EXECUTOR;

    private static class NoCloseInputStream extends FilterInputStream {

        private NoCloseInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {}

        private void close0() throws IOException {
            in.close();
        }

        @Override
        protected void finalize() throws Throwable {
            close0();
        }
    }

    private static class NoCloseOutputStream extends FilterOutputStream {

        private NoCloseOutputStream(@NonNull OutputStream out) {
            super(out);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() {}

        private void close0() throws IOException {
            out.close();
        }

        @Override
        protected void finalize() throws Throwable {
            close0();
        }
    }

    ShellImpl(String... cmd) throws IOException {
        InternalUtils.log(TAG, "exec " + TextUtils.join(" ", cmd));
        status = UNINT;

        process = Runtime.getRuntime().exec(cmd);
        STDIN = new NoCloseOutputStream(process.getOutputStream());
        STDOUT = new NoCloseInputStream(process.getInputStream());
        STDERR = new NoCloseInputStream(process.getErrorStream());

        status = UNKNOWN;

        BufferedReader br = new BufferedReader(new InputStreamReader(STDOUT));

        STDIN.write(("echo SHELL_TEST\n").getBytes("UTF-8"));
        STDIN.flush();
        String s = br.readLine();
        if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST")) {
            throw new IOException();
        }
        status = NON_ROOT_SHELL;

        try {
            STDIN.write(("id\n").getBytes("UTF-8"));
            STDIN.flush();
            s = br.readLine();
            if (TextUtils.isEmpty(s) || !s.contains("uid=0")) {
                throw new IOException();
            }
            status = ROOT_SHELL;
        } catch (IOException ignored) {}

        if (status == ROOT_SHELL && cmd.length >= 2 && TextUtils.equals(cmd[1], "--mount-master"))
            status = ROOT_MOUNT_MASTER;

        br.close();

        token = ShellUtils.genRandomAlphaNumString(32).toString();
        InternalUtils.log(TAG, "token: " + token);
        outGobbler = new StreamGobbler(token, true);
        errGobbler = new StreamGobbler(token, false);
        SERIAL_EXECUTOR = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    @Override
    public void close() throws IOException {
        if (status < UNKNOWN)
            return;
        InternalUtils.log(TAG, "close");
        status = UNINT;
        SERIAL_EXECUTOR.shutdownNow();
        STDIN.close0();
        STDERR.close0();
        STDOUT.close0();
        process.destroy();
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
        ShellUtils.cleanInputStream(STDOUT);
        ShellUtils.cleanInputStream(STDERR);
        if (isAlive())
            task.run(STDIN, STDOUT, STDERR);
    }

    @Override
    public Job newJob(String... cmds) {
        return new JobImpl(new CommandTask(cmds));
    }

    @Override
    public Job newJob(InputStream in) {
        return new JobImpl(new InputStreamTask(in));
    }

    OutputGobblingTask newOutputGobblingTask(String... cmds) {
        return new CommandTask(cmds);
    }

    OutputGobblingTask newOutputGobblingTask(InputStream in) {
        return new InputStreamTask(in);
    }

    abstract class OutputGobblingTask implements Task {

        private ResultImpl res;

        @Override
        public void run(OutputStream stdin, InputStream stdout, InputStream stderr) throws IOException {
            Future<Integer> outFuture = EXECUTOR.submit(outGobbler.set(stdout, res.out));
            Future<Integer> errFuture = res.err == null ? null :
                    EXECUTOR.submit(errGobbler.set(stderr, res.err));
            handleInput(stdin);
            byte[] end = String.format("__RET=$?;echo %s;echo $__RET;__RET=\n", token).getBytes("UTF-8");
            stdin.write(end);
            if (res.err != null) {
                end = String.format("echo %s >&2\n", token).getBytes("UTF-8");
                stdin.write(end);
            }
            stdin.flush();
            try {
                if (errFuture != null)
                    errFuture.get();
                res.code = outFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                throw (InterruptedIOException) new InterruptedIOException().initCause(e);
            }
        }

        void exec() throws IOException {
            ShellImpl.this.execTask(this);
        }

        ExecutorService getExecutor() {
            return ShellImpl.this.SERIAL_EXECUTOR;
        }

        void setResult(ResultImpl res) {
            this.res = res;
        }

        protected abstract void handleInput(OutputStream in) throws IOException;
    }

    private class CommandTask extends OutputGobblingTask {

        private String commands[];

        private CommandTask(String... cmds) {
            commands = cmds;
        }

        @Override
        protected void handleInput(OutputStream in) throws IOException {
            InternalUtils.log(TAG, "CommandTask");
            for (String command : commands) {
                in.write(command.getBytes("UTF-8"));
                in.write('\n');
                in.flush();
                InternalUtils.log(INTAG, command);
            }
        }
    }

    private class InputStreamTask extends OutputGobblingTask {

        private InputStream is;

        private InputStreamTask(InputStream in) {
            is = in;
        }

        @Override
        protected void handleInput(OutputStream in) throws IOException {
            InternalUtils.log(TAG, "InputStreamTask");
            ShellUtils.pump(is, in);
            is.close();
            // Make sure it flushes the shell
            in.write('\n');
            in.flush();
        }
    }
}
