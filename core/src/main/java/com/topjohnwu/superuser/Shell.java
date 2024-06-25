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

package com.topjohnwu.superuser;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.internal.BuilderImpl;
import com.topjohnwu.superuser.internal.MainShell;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.internal.Utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A class providing APIs to an interactive Unix shell.
 * <p>
 * Similar to threads where there is a special "main thread", {@code libsu} also has the
 * concept of the "main shell". For each process, there is a single globally shared
 * "main shell" that is constructed on-demand and cached.
 * <p>
 * To obtain/create the main shell, use the static {@code Shell.getShell(...)} methods.
 * Developers can use these high level APIs to access the main shell:
 * <ul>
 *     <li>{@link #cmd(String...)}</li>
 *     <li>{@link #cmd(InputStream)}</li>
 * </ul>
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

    /* Preserve 2 due to historical reasons */

    @Retention(SOURCE)
    @IntDef({UNKNOWN, NON_ROOT_SHELL, ROOT_SHELL})
    @interface Status {}

    /**
     * If set, create a non-root shell.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_NON_ROOT_SHELL = (1 << 0);
    /**
     * If set, create a root shell with the {@code --mount-master} option.
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_MOUNT_MASTER = (1 << 1);

    /* Preserve (1 << 2) due to historical reasons */
    /* Preserve (1 << 3) due to historical reasons */
    /* Preserve (1 << 4) due to historical reasons */

    @Retention(SOURCE)
    @IntDef(value = {FLAG_NON_ROOT_SHELL, FLAG_MOUNT_MASTER, FLAG_REDIRECT_STDERR}, flag = true)
    @interface ConfigFlags {}

    /**
     * The {@link Executor} that manages all worker threads used in {@code libsu}.
     * <p>
     * Note: If the developer decides to replace the default Executor, keep in mind that
     * each {@code Shell} instance requires at least 3 threads to operate properly.
     */
    @NonNull
    public static Executor EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Set to {@code true} to enable verbose logging throughout the library.
     */
    public static boolean enableVerboseLogging = false;

    /**
     * This flag exists for compatibility reasons. DO NOT use unless necessary.
     * <p>
     * If enabled, STDERR outputs will be redirected to the STDOUT output list
     * when a {@link Job} is configured with {@link Job#to(List)}.
     * Since the {@code Shell.cmd(...)} methods are functionally equivalent to
     * {@code Shell.getShell().newJob().add(...).to(new ArrayList<>())}, this variable
     * also affects the behavior of those methods.
     * <p>
     * Note: The recommended way to redirect STDERR output to STDOUT is to assign the
     * same list to both STDOUT and STDERR with {@link Job#to(List, List)}.
     * The behavior of this flag is unintuitive and error prone.
     */
    public static boolean enableLegacyStderrRedirection = false;

    /**
     * Override the default {@link Builder}.
     * <p>
     * This shell builder will be used to construct the main shell.
     * Set this before the main shell is created anywhere in the program.
     */
    public static void setDefaultBuilder(Builder builder) {
        MainShell.setBuilder(builder);
    }

    /**
     * Get the main shell instance.
     * <p>
     * If {@link #getCachedShell()} returns null, the default {@link Builder} will be used to
     * construct a new {@code Shell}.
     * <p>
     * Unless already cached, this method blocks until the main shell is created.
     * The process could take a very long time (e.g. root permission request prompt),
     * so be extra careful when calling this method from the main thread!
     * <p>
     * A good practice is to "preheat" the main shell during app initialization
     * (e.g. the splash screen) by either calling this method in a background thread or
     * calling {@link #getShell(GetShellCallback)} so subsequent calls to this function
     * returns immediately.
     * @return the cached/created main shell instance.
     * @see Builder#build()
     */
    @NonNull
    public static Shell getShell() {
        return MainShell.get();
    }

    /**
     * Get the main shell instance asynchronously via a callback.
     * <p>
     * If {@link #getCachedShell()} returns null, the default {@link Builder} will be used to
     * construct a new {@code Shell} in a background thread.
     * The cached/created shell instance is returned to the callback on the main thread.
     * @param callback invoked when a shell is acquired.
     */
    public static void getShell(@NonNull GetShellCallback callback) {
        MainShell.get(UiThreadHandler.executor, callback);
    }

    /**
     * Get the main shell instance asynchronously via a callback.
     * <p>
     * If {@link #getCachedShell()} returns null, the default {@link Builder} will be used to
     * construct a new {@code Shell} in a background thread.
     * The cached/created shell instance is returned to the callback executed by provided executor.
     * @param executor the executor used to handle the result callback event.
     *                 If {@code null} is passed, the callback can run on any thread.
     * @param callback invoked when a shell is acquired.
     */
    public static void getShell(@Nullable Executor executor, @NonNull GetShellCallback callback) {
        MainShell.get(executor, callback);
    }

    /**
     * Get the cached main shell.
     * @return a {@code Shell} instance. {@code null} can be returned either when
     * no main shell has been cached, or the cached shell is no longer active.
     */
    @Nullable
    public static Shell getCachedShell() {
        return MainShell.getCached();
    }

    /**
     * Whether the application has access to root.
     * <p>
     * This method returns {@code null} when it is currently unable to determine whether
     * root access has been granted to the application. A non-null value meant that the root
     * permission grant state has been accurately determined and finalized. The application
     * must have at least 1 root shell created to have this method return {@code true}.
     * This method will not block the calling thread; results will be returned immediately.
     * @return whether the application has access to root, or {@code null} when undetermined.
     */
    @Nullable
    public static Boolean isAppGrantedRoot() {
        return Utils.isAppGrantedRoot();
    }

    /* ************
     * Static APIs
     * ************/

    /**
     * Create a pending {@link Job} of the main shell with commands.
     * <p>
     * This method can be treated as functionally equivalent to
     * {@code Shell.getShell().newJob().add(commands).to(new ArrayList<>())}, but the internal
     * implementation is specialized for this use case and does not run this exact code.
     * The developer can manually override output destination(s) with either
     * {@link Job#to(List)} or {@link Job#to(List, List)}.
     * <p>
     * The main shell will NOT be requested until the developer invokes either
     * {@link Job#exec()}, {@link Job#enqueue()}, or {@code Job.submit(...)}. This makes it
     * possible to construct {@link Job}s before the program has created any root shell.
     * @return a job that the developer can execute or submit later.
     * @see Job#add(String...)
     */
    @NonNull
    public static Job cmd(@NonNull String... commands) {
        return MainShell.newJob(commands);
    }

    /**
     * Create a pending {@link Job} of the main shell with an {@link InputStream}.
     * <p>
     * This method can be treated as functionally equivalent to
     * {@code Shell.getShell().newJob().add(in).to(new ArrayList<>())}, but the internal
     * implementation is specialized for this use case and does not run this exact code.
     * The developer can manually override output destination(s) with either
     * {@link Job#to(List)} or {@link Job#to(List, List)}.
     * <p>
     * The main shell will NOT be requested until the developer invokes either
     * {@link Job#exec()}, {@link Job#enqueue()}, or {@code Job.submit(...)}. This makes it
     * possible to construct {@link Job}s before the program has created any root shell.
     * @see Job#add(InputStream)
     */
    @NonNull
    public static Job cmd(@NonNull InputStream in) {
        return MainShell.newJob(in);
    }

    /* ***************
     * Non-static APIs
     * ****************/

    /**
     * Return whether the shell is still alive.
     * @return {@code true} if the shell is still alive.
     */
    public abstract boolean isAlive();

    /**
     * Execute a low-level {@link Task} using the shell. USE THIS METHOD WITH CAUTION!
     * <p>
     * This method exposes raw STDIN/STDOUT/STDERR directly to the developer. This is meant for
     * implementing low-level operations. The shell may stall if the buffer of STDOUT/STDERR
     * is full. It is recommended to use additional threads to consume STDOUT/STDERR in parallel.
     * <p>
     * STDOUT/STDERR is cleared before executing the task. No output from any previous tasks should
     * be left over. It is the developer's responsibility to make sure all operations are done;
     * the shell should be in idle and waiting for further input when the task returns.
     * @param task the desired task.
     * @throws IOException I/O errors when doing operations with STDIN/STDOUT/STDERR
     */
    public abstract void execTask(@NonNull Task task) throws IOException;

    /**
     * Submits a low-level {@link Task} for execution in a queue of the shell.
     * @param task the desired task.
     * @see #execTask(Task)
     */
    public abstract void submitTask(@NonNull Task task);

    /**
     * Construct a new {@link Job} that uses the shell for execution.
     * <p>
     * Unlike {@link #cmd(String...)} and {@link #cmd(InputStream)}, <strong>NO</strong>
     * output will be collected if the developer did not set the output destination with
     * {@link Job#to(List)} or {@link Job#to(List, List)}.
     * @return a job that the developer can execute or submit later.
     */
    @NonNull
    public abstract Job newJob();

    /**
     * Get the status of the shell.
     * @return the status of the shell.
     *         Value is either {@link #UNKNOWN}, {@link #NON_ROOT_SHELL}, or {@link #ROOT_SHELL}
     */
    @Status
    public abstract int getStatus();

    /**
     * Return whether the shell has root access.
     * @return {@code true} if the shell has root access.
     */
    public boolean isRoot() {
        return getStatus() >= ROOT_SHELL;
    }

    /**
     * Wait for any current/pending tasks to finish before closing this shell
     * and release any system resources associated with the shell.
     * <p>
     * Blocks until all current/pending tasks have completed execution, or
     * the timeout occurs, or the current thread is interrupted,
     * whichever happens first.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this shell is terminated and
     *         {@code false} if the timeout elapsed before termination, in which
     *         the shell can still to be used afterwards.
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if interrupted while waiting.
     */
    public abstract boolean waitAndClose(long timeout, @NonNull TimeUnit unit)
            throws IOException, InterruptedException;

    /**
     * Wait indefinitely for any current/pending tasks to finish before closing this shell
     * and release any system resources associated with the shell.
     * @throws IOException if an I/O error occurs.
     */
    public void waitAndClose() throws IOException {
        for (;;) {
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
     * Builder class for {@link Shell} instances.
     * <p>
     * Set the default builder for the main shell instance with
     * {@link #setDefaultBuilder(Builder)}, or directly use a builder object to create new
     * {@link Shell} instances.
     * <p>
     * Do not subclass this class! Use {@link #create()} to get a new Builder object.
     */
    public abstract static class Builder {

        /**
         * Create a new {@link Builder}.
         * @return a new Builder object.
         */
        @NonNull
        public static Builder create() {
            return new BuilderImpl();
        }

        /**
         * Set the desired {@link Initializer}s.
         * @see Initializer
         * @param classes the classes of desired initializers.
         * @return this Builder object for chaining of calls.
         */
        @SafeVarargs
        @NonNull
        public final Builder setInitializers(@NonNull Class<? extends Initializer>... classes) {
            ((BuilderImpl) this).setInitializersImpl(classes);
            return this;
        }

        /**
         * Set flags to control how a new {@code Shell} will be constructed.
         * @param flags the desired flags.
         *              Value is either 0 or bitwise-or'd value of
         *              {@link #FLAG_NON_ROOT_SHELL} or {@link #FLAG_MOUNT_MASTER}
         * @return this Builder object for chaining of calls.
         */
        @NonNull
        public abstract Builder setFlags(@ConfigFlags int flags);

        /**
         * Set the maximum time to wait for shell verification.
         * <p>
         * After the timeout occurs and the shell still has no response,
         * the shell process will be force-closed and throw {@link NoShellException}.
         * @param timeout the maximum time to wait in seconds.
         *                The default timeout is 20 seconds.
         * @return this Builder object for chaining of calls.
         */
        @NonNull
        public abstract Builder setTimeout(long timeout);

        /**
         * Set the commands that will be used to create a new {@code Shell}.
         * @param commands commands that will be passed to {@link Runtime#exec(String[])} to create
         *                 a new {@link Process}.
         * @return this Builder object for chaining of calls.
         */
        @NonNull
        public abstract Builder setCommands(String... commands);

        /**
         * Set the {@link Context} to use when creating a shell.
         * <p>
         * The ContextImpl of the application will be obtained through the provided context,
         * and that will be passed to {@link Initializer#onInit(Context, Shell)}.
         * <p>
         * Calling this method is not usually necessary but recommended, as the library can
         * obtain the current application context through Android internal APIs. However, if your
         * application uses {@link android.R.attr#sharedUserId}, or a shell/root service can be
         * created during the application attach process, then setting a Context explicitly
         * using this method is required.
         * @param context a context of the current package.
         * @return this Builder object for chaining of calls.
         */
        @NonNull
        public final Builder setContext(@NonNull Context context) {
            Utils.setContext(context);
            return this;
        }

        /**
         * Combine all of the options that have been set and build a new {@code Shell} instance.
         * <p>
         * If not {@link #setCommands(String...)}, there are 3 methods to construct a Unix shell;
         * if any method fails, it will fallback to the next method:
         * <ol>
         *     <li>If {@link #FLAG_NON_ROOT_SHELL} is not set and {@link #FLAG_MOUNT_MASTER}
         *     is set, construct a Unix shell by calling {@code su --mount-master}.
         *     It may fail if the root implementation does not support mount master.</li>
         *     <li>If {@link #FLAG_NON_ROOT_SHELL} is not set, construct a Unix shell by calling
         *     {@code su}. It may fail if the device is not rooted, or root permission is
         *     not granted.</li>
         *     <li>Construct a Unix shell by calling {@code sh}. This would never fail in normal
         *     conditions, but should it fail, it will throw {@link NoShellException}</li>
         * </ol>
         * The developer should check the status of the returned {@code Shell} with
         * {@link #getStatus()} since it may be constructed with calling {@code sh}.
         * <p>
         * If {@link #setCommands(String...)} is called, the provided commands will be used to
         * create a new {@link Process} directly. If the process fails to create, or the process
         * is not a valid shell, it will throw {@link NoShellException}.
         * @return the created {@code Shell} instance.
         * @throws NoShellException impossible to construct a {@link Shell} instance, or
         * initialization failed when using the configured {@link Initializer}s.
         */
        @NonNull
        public abstract Shell build();

        /**
         * Combine all of the options that have been set and build a new {@code Shell} instance
         * with the provided commands.
         * @param commands commands that will be passed to {@link Runtime#exec(String[])} to create
         *                 a new {@link Process}.
         * @return the built {@code Shell} instance.
         * @throws NoShellException the provided command cannot create a {@link Shell} instance, or
         * initialization failed when using the configured {@link Initializer}s.
         */
        @NonNull
        public final Shell build(String... commands) {
            return setCommands(commands).build();
        }

        /**
         * Combine all of the options that have been set and build a new {@code Shell} instance
         * with the provided process.
         * @param process a shell {@link Process} that has already been created.
         * @return the built {@code Shell} instance.
         * @throws NoShellException the provided process is not a valid shell, or
         * initialization failed when using the configured {@link Initializer}s.
         */
        @NonNull
        public abstract Shell build(Process process);
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
        public boolean isSuccess() {
            return getCode() == 0;
        }
    }

    /**
     * Represents a shell Job that could later be executed or submitted to background threads.
     * <p>
     * All operations added in {@link #add(String...)} and {@link #add(InputStream)} will be
     * executed in the order of addition.
     */
    public abstract static class Job {

        /**
         * Store output of STDOUT to a specific list.
         * @param stdout the list to store STDOUT. Pass {@code null} to omit all outputs.
         * @return this Job object for chaining of calls.
         */
        @NonNull
        public abstract Job to(@Nullable List<String> stdout);

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
         * <p>
         * This is NOT executing the script like {@code sh script.sh}.
         * This is similar to sourcing the script ({@code . script.sh}) as the
         * raw content of the script is directly fed into STDIN. If you call
         * {@code exit} in the script, <strong>the shell will be killed and this
         * shell instance will no longer be alive!</strong>
         * @param in the InputStream to serve to STDIN.
         *           The stream will be closed after consumption.
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
        public final void submit() {
            submit(null);
        }

        /**
         * Submit the job to an internal queue to run in the background.
         * The result will be returned with a callback running on the main thread.
         * @param cb the callback to receive the result of the job.
         */
        public final void submit(@Nullable ResultCallback cb) {
            submit(UiThreadHandler.executor, cb);
        }

        /**
         * Submit the job to an internal queue to run in the background.
         * The result will be returned with a callback executed by the provided executor.
         * @param executor the executor used to handle the result callback event.
         *                 Pass {@code null} to run the callback on the same thread executing the job.
         * @param cb the callback to receive the result of the job.
         */
        public abstract void submit(@Nullable Executor executor, @Nullable ResultCallback cb);

        /**
         * Submit the job to an internal queue to run in the background.
         * @return a {@link Future} to get the result of the job later.
         */
        @NonNull
        public abstract Future<Result> enqueue();
    }

    /**
     * The initializer when a new {@code Shell} is constructed.
     * <p>
     * This is an advanced feature. If you need to run specific operations when a new {@code Shell}
     * is constructed, extend this class, add your own implementation, and register it with
     * {@link Builder#setInitializers(Class[])}.
     * The concept is similar to {@code .bashrc}: run specific scripts/commands when the shell
     * starts up. {@link #onInit(Context, Shell)} will be called as soon as the shell is
     * constructed and tested as a valid shell.
     * <p>
     * An initializer will be constructed and the callbacks will be invoked each time a new
     * shell is created.
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

    /* **********
     * Interfaces
     * **********/

    /**
     * A task that can be executed by a shell with the method {@link #execTask(Task)}.
     */
    public interface Task {
        /**
         * This method will be called when a task is executed by a shell.
         * Calling {@link Closeable#close()} on any stream is NOP (does nothing).
         * @param stdin the STDIN of the shell.
         * @param stdout the STDOUT of the shell.
         * @param stderr the STDERR of the shell.
         * @throws IOException I/O errors when doing operations with STDIN/STDOUT/STDERR
         */
        void run(@NonNull OutputStream stdin,
                 @NonNull InputStream stdout,
                 @NonNull InputStream stderr) throws IOException;

        /**
         * This method will be called when a shell is unable to execute this task.
         */
        default void shellDied() {}
    }

    /**
     * The callback used in {@link #getShell(GetShellCallback)}.
     */
    public interface GetShellCallback {
        /**
         * @param shell the {@code Shell} obtained in the asynchronous operation.
         */
        void onShell(@NonNull Shell shell);
    }

    /**
     * The callback to receive a result in {@link Job#submit(Shell.ResultCallback)}.
     */
    public interface ResultCallback {
        /**
         * @param out the result of the job.
         */
        void onResult(@NonNull Result out);
    }

    /* ***********
     * Deprecated
     * ***********/

    /**
     * @deprecated Not used anymore
     */
    @Deprecated
    public static final int ROOT_MOUNT_MASTER = 2;

    /**
     * For compatibility, setting this flag will set {@link #enableLegacyStderrRedirection}
     * @deprecated not used anymore
     * @see #enableLegacyStderrRedirection
     */
    @Deprecated
    public static final int FLAG_REDIRECT_STDERR = (1 << 3);

    /**
     * Whether the application has access to root.
     * <p>
     * This method would NEVER produce false negatives, but false positives can be returned before
     * actually constructing a root shell. A {@code false} returned is guaranteed to be
     * 100% accurate, while {@code true} may be returned if the device is rooted, but the user
     * did not grant root access to your application. However, after any root shell is constructed,
     * this method will accurately return {@code true}.
     * @return whether the application has access to root.
     * @deprecated please switch to {@link #isAppGrantedRoot()}
     */
    @Deprecated
    public static boolean rootAccess() {
        return Objects.equals(isAppGrantedRoot(), Boolean.TRUE);
    }
}
