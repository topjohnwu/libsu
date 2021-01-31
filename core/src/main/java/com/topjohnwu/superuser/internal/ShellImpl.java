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
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.topjohnwu.superuser.internal.Utils.UTF_8;

class ShellTerminatedException extends IOException {

    ShellTerminatedException() {
        super("Shell terminated unexpectedly");
    }
}

class ShellImpl extends Shell {
    private static final String TAG = "SHELLIMPL";

    private int status;

    final ExecutorService executor;
    final boolean redirect;
    private final Process process;
    private final NoCloseOutputStream STDIN;
    private final NoCloseInputStream STDOUT;
    private final NoCloseInputStream STDERR;

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

    ShellImpl(long timeout, boolean redirect, String... cmd) throws IOException {
        status = UNKNOWN;
        this.redirect = redirect;

        Utils.log(TAG, "exec " + TextUtils.join(" ", cmd));
        process = Runtime.getRuntime().exec(cmd);
        STDIN = new NoCloseOutputStream(process.getOutputStream());
        STDOUT = new NoCloseInputStream(process.getInputStream());
        STDERR = new NoCloseInputStream(process.getErrorStream());
        executor = new SerialExecutorService();

        if (cmd.length >= 2 && TextUtils.equals(cmd[1], "--mount-master"))
            status = ROOT_MOUNT_MASTER;

        // Shell checks might get stuck indefinitely
        Future<Void> check = executor.submit(this::shellCheck);
        try {
            try {
                check.get(timeout, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw new IOException("Unknown ExecutionException", cause);
                }
            } catch (TimeoutException e) {
                throw new IOException("Shell timeout", e);
            } catch (InterruptedException e) {
                throw new IOException("Shell initialization interrupted", e);
            }
        } catch (IOException e) {
            executor.shutdownNow();
            release();
            throw e;
        }
    }

    private Void shellCheck() throws IOException {
        // Clean up potential garbage from InputStreams
        ShellUtils.cleanInputStream(STDOUT);
        ShellUtils.cleanInputStream(STDERR);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(STDOUT))) {

            STDIN.write(("echo SHELL_TEST\n").getBytes(UTF_8));
            STDIN.flush();
            String s = br.readLine();
            if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST"))
                throw new IOException("Created process is not a shell");
            int status = NON_ROOT_SHELL;

            STDIN.write(("id\n").getBytes(UTF_8));
            STDIN.flush();
            s = br.readLine();
            if (!TextUtils.isEmpty(s) && s.contains("uid=0"))
                status = ROOT_SHELL;

            if (status == ROOT_SHELL && this.status == ROOT_MOUNT_MASTER)
                status = ROOT_MOUNT_MASTER;

            this.status = status;
        }
        return null;
    }

    private void release() {
        status = UNKNOWN;
        try { STDIN.close0(); } catch (IOException ignored) {}
        try { STDERR.close0(); } catch (IOException ignored) {}
        try { STDOUT.close0(); } catch (IOException ignored) {}
        process.destroy();
    }

    @Override
    public boolean waitAndClose(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        if (status < 0)
            return true;
        executor.shutdown();
        if (executor.awaitTermination(timeout, unit)) {
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
        executor.shutdownNow();
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

}
