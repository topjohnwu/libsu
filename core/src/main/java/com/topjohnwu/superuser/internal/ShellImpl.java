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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.ArrayDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class ShellImpl extends Shell {
    private volatile int status;

    private final Process process;
    private final NoCloseOutputStream STDIN;
    private final NoCloseInputStream STDOUT;
    private final NoCloseInputStream STDERR;

    // Guarded by scheduleLock
    private final ReentrantLock scheduleLock = new ReentrantLock();
    private final Condition idle = scheduleLock.newCondition();
    private final ArrayDeque<Task> tasks = new ArrayDeque<>();
    private boolean isRunningTask = false;

    private static final class SyncTask implements Task {

        private final Condition condition;
        private boolean set = false;

        SyncTask(Condition c) {
            condition = c;
        }

        void signal() {
            set = true;
            condition.signal();
        }

        void await() {
            while (!set) {
                try {
                    condition.await();
                } catch (InterruptedException ignored) {}
            }
        }

        @Override
        public void run(OutputStream stdin, InputStream stdout, InputStream stderr) {}
    }

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

    ShellImpl(BuilderImpl builder, Process proc) throws IOException {
        status = UNKNOWN;
        process = proc;
        STDIN = new NoCloseOutputStream(proc.getOutputStream());
        STDOUT = new NoCloseInputStream(proc.getInputStream());
        STDERR = new NoCloseInputStream(proc.getErrorStream());

        // Shell checks might get stuck indefinitely
        FutureTask<Integer> check = new FutureTask<>(this::shellCheck);
        EXECUTOR.execute(check);
        try {
            try {
                status = check.get(builder.timeout, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw new IOException("Unknown ExecutionException", cause);
                }
            } catch (TimeoutException e) {
                throw new IOException("Shell check timeout", e);
            } catch (InterruptedException e) {
                throw new IOException("Shell check interrupted", e);
            }
        } catch (IOException e) {
            release();
            throw e;
        }
    }

    private Integer shellCheck() throws IOException {
        try {
            process.exitValue();
            throw new IOException("Created process has terminated");
        } catch (IllegalThreadStateException ignored) {
            // Process is alive
        }

        // Clean up potential garbage from InputStreams
        ShellUtils.cleanInputStream(STDOUT);
        ShellUtils.cleanInputStream(STDERR);

        int status = NON_ROOT_SHELL;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(STDOUT))) {

            STDIN.write(("echo SHELL_TEST\n").getBytes(UTF_8));
            STDIN.flush();
            String s = br.readLine();
            if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST"))
                throw new IOException("Created process is not a shell");

            STDIN.write(("id\n").getBytes(UTF_8));
            STDIN.flush();
            s = br.readLine();
            if (!TextUtils.isEmpty(s) && s.contains("uid=0")) {
                status = ROOT_SHELL;
                Utils.setConfirmedRootState(true);
                // noinspection ConstantConditions
                String cwd = ShellUtils.escapedString(System.getProperty("user.dir"));
                STDIN.write(("cd " + cwd + "\n").getBytes(UTF_8));
                STDIN.flush();
            }
        }
        return status;
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

        scheduleLock.lock();
        try {
            if (isRunningTask && !idle.await(timeout, unit))
                return false;
            close();
        } finally {
            scheduleLock.unlock();
        }

        return true;
    }

    @Override
    public void close() {
        if (status < 0)
            return;
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
            release();
            return false;
        } catch (IllegalThreadStateException e) {
            // Process is still running
            return true;
        }
    }

    private synchronized void exec0(@NonNull Task task) throws IOException {
        if (status < 0) {
            task.shellDied();
            return;
        }

        ShellUtils.cleanInputStream(STDOUT);
        ShellUtils.cleanInputStream(STDERR);
        try {
            STDIN.write('\n');
            STDIN.flush();
        } catch (IOException e) {
            release();
            task.shellDied();
            return;
        }

        task.run(STDIN, STDOUT, STDERR);
    }

    private void processTasks() {
        Task task;
        while ((task = processNextTask(false)) != null) {
            try {
                exec0(task);
            } catch (IOException ignored) {}
        }
    }

    @Nullable
    private Task processNextTask(boolean fromExec) {
        scheduleLock.lock();
        try {
            final Task task = tasks.poll();
            if (task == null) {
                isRunningTask = false;
                idle.signalAll();
                return null;
            }
            if (task instanceof SyncTask) {
                ((SyncTask) task).signal();
                return null;
            }
            if (fromExec) {
                // Put the task back in front of the queue
                tasks.offerFirst(task);
            } else {
                return task;
            }
        } finally {
            scheduleLock.unlock();
        }
        EXECUTOR.execute(this::processTasks);
        return null;
    }

    @Override
    public void submitTask(@NonNull Task task) {
        scheduleLock.lock();
        try {
            tasks.offer(task);
            if (!isRunningTask) {
                isRunningTask = true;
                EXECUTOR.execute(this::processTasks);
            }
        } finally {
            scheduleLock.unlock();
        }
    }

    @Override
    public void execTask(@NonNull Task task) throws IOException {
        scheduleLock.lock();
        try {
            if (isRunningTask) {
                SyncTask sync = new SyncTask(scheduleLock.newCondition());
                tasks.offer(sync);
                // Wait until it's our turn
                sync.await();
            }
            isRunningTask = true;
        } finally {
            scheduleLock.unlock();
        }
        exec0(task);
        processNextTask(true);
    }

    @NonNull
    @Override
    public Job newJob() {
        return new ShellJob(this);
    }
}
