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

import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

class ShellImpl extends Shell {
    private static final String TAG = "SHELLIMPL";
    private static final String INTAG = "SHELL_IN";
    private static final int UNINT = -2;

    final Process process;
    final OutputStream STDIN;
    final InputStream STDOUT;
    final InputStream STDERR;
    final ReentrantLock lock;

    private CharSequence token;
    private StreamGobbler outGobbler;
    private StreamGobbler errGobbler;

    ShellImpl(String... cmd) throws IOException {
        ShellUtils.log(TAG, "exec " + TextUtils.join(" ", cmd));
        status = UNINT;

        process = Runtime.getRuntime().exec(cmd);
        STDIN = process.getOutputStream();
        STDOUT = process.getInputStream();
        STDERR = process.getErrorStream();

        token = ShellUtils.genRandomAlphaNumString(32);
        ShellUtils.log(TAG, "token: " + token);
        outGobbler = new StreamGobbler(STDOUT, token);
        errGobbler = new StreamGobbler(STDERR, token);

        lock = new ReentrantLock();
        status = UNKNOWN;

        testShell();
        status = NON_ROOT_SHELL;

        try {
            testRootShell();
            status = ROOT_SHELL;
        } catch (IOException ignored) {}
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    @Override
    public void close() throws IOException {
        if (status < UNKNOWN)
            return;
        ShellUtils.log(TAG, "close");
        status = UNINT;
        outGobbler.interrupt();
        errGobbler.interrupt();
        STDIN.close();
        STDERR.close();
        STDOUT.close();
        process.destroy();
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
    public void run(List<String> output, List<String> error,
                    @NonNull final String... commands) {
        run_sync_output(output, error, () ->
                run_commands(output != null, error != null, commands));
    }

    @Override
    public void run(List<String> output, List<String> error, Async.Callback callback,
                    @NonNull final String... commands) {
        run_async_task(output, error, callback, () ->
                run_commands(output != null, error != null, commands));
    }

    @Override
    public void loadInputStream(List<String> output, List<String> error, @NonNull InputStream in) {
        run_sync_output(output, error, new LoadInputStream(in));
    }

    @Override
    public void loadInputStream(List<String> output, List<String> error, Async.Callback callback,
                                @NonNull InputStream in) {
        run_async_task(output, error, callback, new LoadInputStream(in));
    }

    private void testShell() throws IOException {
        STDIN.write(("echo SHELL_TEST\n").getBytes("UTF-8"));
        STDIN.flush();
        String s = new BufferedReader(new InputStreamReader(STDOUT)).readLine();
        if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST")) {
            throw new IOException();
        }
    }

    private void testRootShell() throws IOException {
        STDIN.write(("id\n").getBytes("UTF-8"));
        STDIN.flush();
        String s = new BufferedReader(new InputStreamReader(STDOUT)).readLine();
        if (TextUtils.isEmpty(s) || !s.contains("uid=0")) {
            throw new IOException();
        }
    }

    private void run_commands(boolean stdout, boolean stderr, String... commands) {
        String suffix = (stdout ? "" : " >/dev/null") + (stderr ? "" : " 2>/dev/null") + "\n";
        lock.lock();
        try {
            if (!isAlive())
                return;
            ShellUtils.log(TAG, "run_commands");
            for (String command : commands) {
                STDIN.write((command + suffix).getBytes("UTF-8"));
                STDIN.flush();
                ShellUtils.log(INTAG, command);
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                close();
            } catch (IOException ignored) {}
        } finally {
            lock.unlock();
        }
    }

    private void run_sync_output(List<String> output, List<String> error, Runnable task) {
        lock.lock();
        try {
            if (!isAlive())
                return;
            ShellUtils.log(TAG, "run_sync_output");
            outGobbler.begin(output);
            if (error != null)
                errGobbler.begin(error);
            task.run();
            byte[] finalize = String.format("echo %s; echo %s >&2\n", token, token)
                    .getBytes("UTF-8");
            STDIN.write(finalize);
            STDIN.flush();
            outGobbler.waitDone();
            errGobbler.waitDone();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            try {
                close();
            } catch (IOException ignored) {}
        } finally {
            lock.unlock();
        }
    }

    private void run_async_task(List<String> output, List<String> error,
                                Async.Callback callback, Runnable task) {
        ShellUtils.log(TAG, "run_async_task");
        final Handler handler = ShellUtils.onMainThread() ? new Handler() : null;
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            if (output == null && error == null) {
                // Without any output request, we simply run the task
                task.run();
            } else {
                run_sync_output(output, error, task);
                if (callback != null) {
                    Runnable acb = () -> callback.onTaskResult(
                            output == null ? null : Collections.synchronizedList(output),
                            error == null ? null : (error == output ? null :
                                    Collections.synchronizedList(error))
                    );
                    if (handler == null)
                        acb.run();
                    else
                        handler.post(acb);
                }
            }
        });
    }

    private class LoadInputStream implements Runnable {

        private InputStream in;

        LoadInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            ShellUtils.log(TAG, "loadInputStream");
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read;
                byte buffer[] = new byte[4096];
                while ((read = in.read(buffer)) > 0)
                    baos.write(buffer, 0, read);
                in.close();
                baos.writeTo(STDIN);
                // Make sure it flushes the shell
                STDIN.write('\n');
                STDIN.flush();
                ShellUtils.log(INTAG, baos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
