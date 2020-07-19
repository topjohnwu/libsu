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

package com.topjohnwu.superuser;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.internal.Impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A class providing an API to an interactive (root) shell.
 * <p>
 * In most cases, developers do not need to directly access a {@code Shell} instance.
 * Use these high level APIs instead:
 * <ul>
 *     <li>{@link #sh(String...)}</li>
 *     <li>{@link #su(String...)}</li>
 *     <li>{@link #sh(InputStream)}</li>
 *     <li>{@link #su(InputStream)}</li>
 * </ul>
 * These methods not only uses the global shell instance but are also more convenient to use.
 * The global shell instance is constructed on-demand and cached.
 * To get the instance directly, call {@link #getShell()} or {@link #getShell(GetShellCallback)}.
 * <p>
 * Developers can check the example that came along with the library, it demonstrates many features
 * the library has to offer.
 */

public abstract class Shell implements Closeable {

    /**
     * Shell status: Unknown. One possible result of {@link #getStatus()}.
     * <p>
     * Constant value {@value}.
     */
    public static final int UNKNOWN = -1;
    /**
     * Shell status: Non-root shell. One possible result of {@link #getStatus()}.
     * <p>
     * Constant value {@value}.
     */
    public static final int NON_ROOT_SHELL = 0;
    /**
     * Shell status: Root shell. One possible result of {@link #getStatus()}.
     * <p>
     * Constant value {@value}.
     */
    public static final int ROOT_SHELL = 1;
    /**
     * Shell status: Root shell with mount master enabled. One possible result of {@link #getStatus()}.
     * <p>
     * Constant value {@value}.
     */
    public static final int ROOT_MOUNT_MASTER = 2;
    /**
     * If set, create a non-root shell by default.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_NON_ROOT_SHELL = 0x01;
    /**
     * If set, create a root shell with {@code --mount-master} option.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_MOUNT_MASTER = 0x02;
    /**
     * If set, verbose log everything.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_VERBOSE_LOGGING = 0x04;
    /**
     * If set, STDERR outputs will be stored in STDOUT outputs.
     * <p>
     * Note: This flag only affects the following methods:
     * <ul>
     *     <li>{@link #sh(String...)}</li>
     *     <li>{@link #su(String...)}</li>
     *     <li>{@link #sh(InputStream)}</li>
     *     <li>{@link #su(InputStream)}</li>
     *     <li>{@link Job#to(List)}</li>
     * </ul>
     * Check the descriptions of each method above for more details.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_REDIRECT_STDERR = 0x08;

    /**
     * @deprecated
     * {@code /sbin/.magisk/busybox} will be removed in a future Magisk version, please
     * directly bundle BusyBox in your app with {@link com.topjohnwu.superuser.BusyBoxInstaller}
     * <p>
     * If set, {@code /sbin/.magisk/busybox} will be prepended to {@code PATH}.
     * This will make the shell use Magisk's internal busybox.
     * <p>
     * Constant value {@value}.
     */
    @Deprecated
    public static final int FLAG_USE_MAGISK_BUSYBOX = 0x10;

    /**
     * The {@link ExecutorService} that manages all worker threads used in {@code libsu}.
     * <p>
     * Note: If the developer decides to replace the default ExecutorService, keep in mind that
     * each {@code Shell} instance requires at least 3 threads to operate properly.
     */
    @NonNull
    public static ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Get {@code Shell} via {@link #getCachedShell()} or create new if required.
     * If {@link #getCachedShell()} returns null, it will call {@link #newInstance()} to construct
     * a new {@code Shell}.
     * @see #newInstance()
     * @return a {@code Shell} instance
     */
    @NonNull
    public static Shell getShell() {
        return Impl.getShell();
    }

    /**
     * Get {@code Shell} via {@link #getCachedShell()} or create new if required, returns via callback.
     * If {@link #getCachedShell()} does not return null, the callback will be called immediately,
     * or else it will call {@link #newInstance()} in a background thread and invoke the callback
     * in the main thread.
     * @param callback called when a shell is acquired.
     */
    public static void getShell(@NonNull GetShellCallback callback) {
        Impl.getShell(callback);
    }

    /**
     * Get the cached global {@code Shell} instance
     * @return a {@code Shell} instance, {@code null} if no active shell is cached.
     */
    @Nullable
    public static Shell getCachedShell() {
        return Impl.cached();
    }

    /**
     * Construct a new {@code Shell} instance with the default methods.
     * <p>
     * There are 3 methods to construct a Unix shell; if any method fails, it will fallback to the
     * next method:
     * <ol>
     *     <li>If {@link #FLAG_NON_ROOT_SHELL} is not set and {@link #FLAG_MOUNT_MASTER}
     *     is set, construct a Unix shell by calling {@code su --mount-master}.
     *     It may fail if the root implementation does not support mount master.</li>
     *     <li>If {@link #FLAG_NON_ROOT_SHELL} is not set, construct a Unix shell by calling
     *     {@code su}. It may fail if the device is not rooted, or root permission is not granted.</li>
     *     <li>Construct a Unix shell by calling {@code sh}. This would never fail in normal
     *     conditions, but should it fails, it will throw {@link NoShellException}</li>
     * </ol>
     * The developer should check the status of the returned {@code Shell} with {@link #getStatus()}
     * since it may return the result of any of the 3 possible methods.
     * @return a new {@code Shell} instance.
     * @throws NoShellException impossible to construct a {@link Shell} instance, or initialization
     * failed when using the configured {@link Initializer}.
     */
    @NonNull
    public static Shell newInstance() {
        return Impl.newShell();
    }

    /**
     * Construct a new {@code Shell} instance with provided commands.
     * @param commands commands that will be passed to {@link Runtime#exec(String[])} to create
     *                 a new {@link Process}.
     * @return a new {@code Shell} instance.
     * @throws NoShellException the provided command cannot create a {@link Shell} instance, or
     * initialization failed when using the configured {@link Initializer}.
     */
    @NonNull
    public static Shell newInstance(String... commands) {
        return Impl.newShell(commands);
    }

    /**
     * Return whether the global shell has root access.
     * @return {@code true} if the global shell has root access.
     */
    public static boolean rootAccess() {
        try {
            return getShell().isRoot();
        } catch (NoShellException e) {
            return false;
        }

    }

    /* ************
     * Static APIs
     * ************/

    /**
     * Equivalent to {@link #sh(String...)} with root access check.
     */
    @NonNull
    public static Job su(@NonNull String... commands) {
        return Impl.newJob(true, commands);
    }

    /**
     * Create a {@link Job} with commands.
     * <p>
     * By default a new {@link List} will be created internally
     * to store the output after executing {@link Job#exec()} or {@link Job#submit(Shell.ResultCallback)}.
     * You can get the internally created {@link List} via {@link Result#getOut()} after
     * the job is done. Output of STDERR will be stored in the same list along
     * with STDOUT if the flag {@link #FLAG_REDIRECT_STDERR} is set; {@link Result#getErr()}
     * will always return an empty list.
     * <p>
     * Note: the behavior mentioned above <strong>DOES NOT</strong> apply if the developer manually
     * override output destination with either {@link Job#to(List)} or {@link Job#to(List, List)}.
     * <p>
     * {@code Shell} will not be requested until the developer invokes either {@link Job#exec()},
     * {@link Job#submit()}, or {@link Job#submit(Shell.ResultCallback)}. It is possible to construct
     * complex {@link Job} before the program will request any root access.
     * <p>
     * If the developer plan to simply construct a job and add operations with
     * {@code Job.add(InputStream/String...))} afterwards, call this method with no arguments.
     * @param commands the commands to run within the {@link Job}.
     * @return a job that the developer can execute or submit later.
     */
    @NonNull
    public static Job sh(@NonNull String... commands) {
        return Impl.newJob(false, commands);
    }

    /**
     * Equivalent to {@link #sh(InputStream)} with root access check.
     */
    @NonNull
    public static Job su(@NonNull InputStream in) {
        return Impl.newJob(true, in);
    }

    /**
     * Create a {@link Job} with an {@link InputStream}.
     * Check {@link #sh(String...)} for details.
     * @param in the data in this {@link InputStream} will be served to {@code STDIN}.
     * @return a job that the developer can execute or submit later.
     */
    @NonNull
    public static Job sh(@NonNull InputStream in) {
        return Impl.newJob(false, in);
    }

    /* ***************
     * Non-static APIs
     * ****************/

    /**
     * Return whether the {@code Shell} is still alive.
     * @return {@code true} if the {@code Shell} is still alive.
     */
    public abstract boolean isAlive();

    /**
     * Execute a {@code Task} with the shell. USE THIS METHOD WITH CAUTION!
     * <p>
     * This method exposes raw STDIN/STDOUT/STDERR directly to the task. This is meant for
     * implementing low-level operations. Operation may stall if the buffer of STDOUT/STDERR
     * is full, so it is recommended to use separate threads to read STDOUT/STDERR if you expect
     * large outputs.
     * <p>
     * STDOUT/STDERR is cleared before executing the task. No output from any previous tasks should
     * be left over. It is the developer's responsibility to make sure all operations are done:
     * the shell should be in idle and waiting for further input to be sent to STDIN when the task
     * returns.
     * @param task the desired task.
     * @throws IOException I/O errors when doing operations with STDIN/STDOUT/STDERR
     */
    public abstract void execTask(@NonNull Task task) throws IOException;

    /**
     * Construct a new {@link Job} that will use the shell for execution.
     * <p>
     * Be aware that unlike {@code Shell.su(...)/Shell.sh(...)}, <strong>NO</strong> output will
     * be produced if the developer did not set the output destination with {@link Job#to(List)}
     * or {@link Job#to(List, List)}.
     * @return a job that the developer can execute or submit later.
     */
    @NonNull
    public abstract Job newJob();

    /**
     * Get the status of the shell.
     * @return the status of the shell.
     *         Value is either {@link #UNKNOWN}, {@link #NON_ROOT_SHELL}, {@link #ROOT_SHELL}, or
     *         {@link #ROOT_MOUNT_MASTER}
     */
    public abstract int getStatus();

    /**
     * Return whether the shell has root access.
     * @return {@code true} if the shell has root access.
     */
    public boolean isRoot() {
        return getStatus() >= ROOT_SHELL;
    }

    /**
     * Wait for all tasks to be done before closing this shell
     * and releasing any system resources associated with the shell.
     * <p>
     * Blocks until all tasks have completed execution, or
     * the timeout occurs, or the current thread is interrupted,
     * whichever happens first.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this shell is terminated and
     *         {@code false} if the timeout elapsed before termination.
     *         The shell can still to be used afterwards in this case.
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if interrupted while waiting.
     */
    public abstract boolean waitAndClose(long timeout, @NonNull TimeUnit unit)
            throws IOException, InterruptedException;

    /**
     * Wait for all tasks to be done indefinitely before closing the shell
     * and releasing any system resources associated with the shell.
     * @throws IOException if an I/O error occurs.
     */
    public void waitAndClose() throws IOException {
        while (true) {
            try {
                if (waitAndClose(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
                    break;
            } catch (InterruptedException ignored) {}
        }
    }

    /* **************
    * Nested classes
    * ***************/

    /**
     * Static methods for configuring the behavior of {@link Shell}.
     */
    public static final class Config {

        private Config() {}

        /**
         * Set the desired {@link Initializer}s.
         * @see Initializer
         * @param classes the classes of desired initializers.
         */
        @SafeVarargs
        public static void setInitializers(@NonNull Class<? extends Initializer>... classes) {
            Impl.initClasses.clear();
            Impl.initClasses.addAll(Arrays.asList(classes));
        }

        /**
         * @deprecated
         * Add additional {@code Initializer}s.
         * @see Initializer
         * @param classes the classes of desired initializers.
         */
        @Deprecated
        @SafeVarargs
        public static void addInitializers(@NonNull Class<? extends Initializer>... classes) {
            Impl.initClasses.addAll(Arrays.asList(classes));
        }

        /**
         * Set flags that controls how {@code Shell} works and how a new {@code Shell} will be
         * constructed.
         * @param flags the desired flags.
         *              Value is either 0 or bitwise-or'd value of {@link #FLAG_NON_ROOT_SHELL},
         *              {@link #FLAG_VERBOSE_LOGGING}, {@link #FLAG_MOUNT_MASTER}, or
         *              {@link #FLAG_REDIRECT_STDERR}
         */
        public static void setFlags(int flags) {
            Impl.flags = flags;
        }

        /**
         * @deprecated
         * Get the flags that controls how {@code Shell} works and how a new {@code Shell} will be
         * constructed.
         * @return the flags
         */
        @Deprecated
        public static int getFlags() {
            return Impl.flags;
        }

        /**
         * Set whether enable verbose logging.
         * <p>
         * This is just a handy function to toggle verbose logging with a boolean value.
         * For example: {@code Shell.verboseLogging(BuildConfig.DEBUG)}.
         * @param verbose if true, adds {@link #FLAG_VERBOSE_LOGGING} to flags.
         */
        public static void verboseLogging(boolean verbose) {
            if (verbose)
                Impl.flags |= FLAG_VERBOSE_LOGGING;
        }

        /**
         * Set the maximum time to wait for a new shell construction.
         * <p>
         * After the timeout occurs and the new shell still have no response,
         * the shell process will be force-closed and throw {@link NoShellException}.
         * @param timeout the maximum time to wait in seconds.
         *                The default timeout is 20 seconds.
         */
        public static void setTimeout(long timeout) {
            Impl.timeout = timeout;
        }
    }

    /**
     * A task that can be executed by a shell with the method {@link #execTask(Task)}.
     */
    public interface Task {
        /**
         * This method will be called when a task is executed by a shell.
         * {@link Closeable#close()} on all streams is NOP, it is safe to close them.
         * @param stdin the STDIN of the shell.
         * @param stdout the STDOUT of the shell.
         * @param stderr the STDERR of the shell.
         * @throws IOException I/O errors when doing operations with STDIN/STDOUT/STDERR
         */
        void run(@NonNull OutputStream stdin,
                 @NonNull InputStream stdout,
                 @NonNull InputStream stderr) throws IOException;
    }

    /**
     * The result of a {@link Job}.
     */
    public abstract static class Result {

        /**
         * This code indicates that the job was not executed, and the outputs are all empty.
         * Constant value: {@value}.
         */
        public static final int JOB_NOT_EXECUTED = -1;

        /**
         * Get the output of STDOUT.
         * @return a list of strings that stores the output of STDOUT. Empty list if no output
         * is available.
         */
        @NonNull
        public abstract List<String> getOut();

        /**
         * Get the output of STDERR.
         * @return a list of strings that stores the output of STDERR. Empty list if no output
         * is available.
         */
        @NonNull
        public abstract List<String> getErr();

        /**
         * Get the return code of the job.
         * @return the return code of the last operation in the shell. If the job is executed
         * properly, the code should range from 0-255. If the job fails to execute, it will return
         * {@link #JOB_NOT_EXECUTED}.
         */
        public abstract int getCode();

        /**
         * Whether the job succeeded.
         * {@code getCode() == 0}.
         * @return {@code true} if the return code is 0.
         */
        public abstract boolean isSuccess();
    }

    /**
     * The callback to receive a result in {@link Job#submit(Shell.ResultCallback)}.
     */
    public interface ResultCallback {

        /**
         * @param out the result of the job.
         */
        @MainThread
        void onResult(@NonNull Result out);
    }

    /**
     * Represents a Job that could later be executed or submitted to background thread.
     * <p>
     * All operations added in {@link #add(String...)} and {@link #add(InputStream)} will be
     * executed in the order of addition.
     */
    public abstract static class Job {

        /**
         * Store output to a specific list.
         * <p>
         * Output of STDERR will be also be stored in the same {@link List} if the flag
         * {@link #FLAG_REDIRECT_STDERR} is set; {@link Result#getErr()}
         * will always return an empty list.
         * @param output the list to store outputs. Pass {@code null} to omit all outputs.
         * @return this Job object for chaining of calls.
         */
        @NonNull
        public abstract Job to(@Nullable List<String> output);

        /**
         * Store output of STDOUT and STDERR to specific lists.
         * @param stdout the list to store STDOUT. Pass {@code null} to omit STDOUT.
         * @param stderr the list to store STDERR. Pass {@code null} to omit STDERR.
         * @return this Job object for chaining of calls.
         */
        @NonNull
        public abstract Job to(@Nullable List<String> stdout, @Nullable List<String> stderr);

        /**
         * Add a new operation running commands.
         * @param cmds the commands to run.
         * @return this Job object for chaining of calls.
         */
        @NonNull
        public abstract Job add(@NonNull String... cmds);

        /**
         * Add a new operation serving an InputStream to STDIN.
         * @param in the InputStream to serve to STDIN.
         * @return this Job object for chaining of calls.
         */
        @NonNull
        public abstract Job add(@NonNull InputStream in);

        /**
         * Execute the job immediately and returns the result.
         * @return the result of the job.
         */
        @NonNull
        public abstract Result exec();

        /**
         * Submit the job to an internal queue to run in the background.
         * The result will be omitted.
         */
        public abstract void submit();

        /**
         * Submit the job to an internal queue to run in the background.
         * The result will be returned to the callback, running in the main thread.
         * @param cb the callback to receive the result of the job.
         */
        public abstract void submit(@Nullable ResultCallback cb);
    }

    /**
     * The initializer when a new {@code Shell} is constructed.
     * <p>
     * This is an advanced feature. If you need to run specific operations when a new {@code Shell}
     * is constructed, extend this class, add your own implementation, and register it with
     * {@link Config#addInitializers(Class[])} or {@link Config#setInitializers(Class[])}.
     * The concept is a bit like {@code .bashrc}: a specific script/command will run when the shell
     * starts up. {@link #onInit(Context, Shell)} will be called as soon as the {@code Shell} is
     * constructed and tested as a valid shell.
     * <p>
     * An initializer will be constructed and the callbacks will be invoked each time a new
     * {@code Shell} is created. A {@code Context} will be passed to the callbacks, use it to
     * access resources within the APK (e.g. shell scripts).
     */
    public static class Initializer {

        /**
         * Called when a new shell is constructed.
         * @param context the application context.
         * @param shell the newly constructed shell.
         * @return {@code false} when initialization fails, otherwise {@code true}.
         */
        public boolean onInit(@NonNull Context context, @NonNull Shell shell) { return true; }
    }

    /**
     * The callback used in {@link #getShell(GetShellCallback)}.
     */
    public interface GetShellCallback {
        /**
         * @param shell the {@code Shell} obtained in the asynchronous operation.
         */
        @MainThread
        void onShell(@NonNull Shell shell);
    }
}
