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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ShellImpl extends Shell {
    private static final String TAG = "SHELLIMPL";
    private static final int UNINT = -2;

    /* -1: not determined
    * 0: unknown, use fallback
    * 1: Java 7 (Integer exitValue)
    * 2: Java 8 (boolean hasExited)
    * */
    private static int processImpl = -1;
    private static Field processStatus;

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
            super(out);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() {}

        void close0() throws IOException {
            out.close();
        }
    }

    ShellImpl(long timeout, String... cmd) throws IOException {
        InternalUtils.log(TAG, "exec " + TextUtils.join(" ", cmd));
        status = UNINT;

        String token = ShellUtils.genRandomAlphaNumString(32).toString();
        InternalUtils.log(TAG, "token: " + token);
        outGobbler = new StreamGobbler(token, true);
        errGobbler = new StreamGobbler(token, false);
        endCmd = String.format("__RET=$?;echo %s;echo %s >&2;echo $__RET;__RET=\n", token, token).getBytes("UTF-8");
        SERIAL_EXECUTOR = Executors.newSingleThreadExecutor();

        process = Runtime.getRuntime().exec(cmd);
        STDIN = new NoCloseOutputStream(process.getOutputStream());
        STDOUT = new NoCloseInputStream(process.getInputStream());
        STDERR = new NoCloseInputStream(process.getErrorStream());

        // Try to get the field of the process status
        if (processImpl < 0) {
            Class<?> clazz = process.getClass();
            try {
                // Java 8 UNIXProcess
                processStatus = clazz.getDeclaredField("hasExited");
                processImpl = 2;
            } catch (NoSuchFieldException e) {
                // Java 7 ProcessManager$ProcessImpl
                try {
                    processStatus = clazz.getDeclaredField("exitValue");
                    processImpl = 1;
                } catch (NoSuchFieldException e1) {
                    processImpl = 0;
                }
            }
            if (processImpl > 0) {
                processStatus.setAccessible(true);
            }
        }

        status = UNKNOWN;

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
            throw (IOException) e.getCause();
        } catch (InterruptedException|TimeoutException e) {
            SERIAL_EXECUTOR.shutdownNow();
            release();
            InternalUtils.stackTrace(e);
            throw new IOException("Shell timeout");
        }
    }

    private void release() throws IOException {
        status = UNINT;
        STDIN.close0();
        STDERR.close0();
        STDOUT.close0();
        process.destroy();
    }

    @Override
    public boolean waitAndClose(long timeout, TimeUnit unit) throws InterruptedException, IOException {
        if (status < UNKNOWN)
            return true;
        InternalUtils.log(TAG, "waitAndClose");
        SERIAL_EXECUTOR.shutdown();
        if (SERIAL_EXECUTOR.awaitTermination(timeout, unit)) {
            release();
            return true;
        } else {
            status = UNINT;
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        if (status < UNKNOWN)
            return;
        InternalUtils.log(TAG, "close");
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

        switch (processImpl) {
            case 1:
                /* Integer exitValue */
                try {
                    return processStatus.get(process) == null;
                } catch (IllegalAccessException e) {
                    /* Impossible */
                    return false;
                }
            case 2:
                /* boolean hasExited */
                try {
                    return !processStatus.getBoolean(process);
                } catch (IllegalAccessException e) {
                    /* Impossible */
                    return false;
                }
            case 0:
            default:
                try {
                    process.exitValue();
                    // Process is dead, shell is not alive
                    return false;
                } catch (IllegalThreadStateException e) {
                    // Process is still running
                    return true;
                }
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
    public Job newJob() {
        return new JobImpl(this);
    }

    Task newTask(List<InputHandler> handlers, ResultImpl res) {
        return new DefaultTask(handlers, res);
    }

    private class DefaultTask implements Task {

        private ResultImpl res;
        private List<InputHandler> handlers;

        DefaultTask(List<InputHandler> h, ResultImpl r) {
            handlers = h;
            res = r;
        }

        @Override
        public void run(OutputStream stdin, InputStream stdout, InputStream stderr) throws IOException {
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
