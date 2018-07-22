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

package com.topjohnwu.superuser;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.topjohnwu.superuser.internal.Factory;
import com.topjohnwu.superuser.internal.InternalUtils;
import com.topjohnwu.superuser.internal.NOPJob;
import com.topjohnwu.superuser.internal.NOPList;
import com.topjohnwu.superuser.internal.ShellCompat;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * A class providing an API to an interactive (root) shell.
 * <p>
 * This class can be put into 3 categories: the static utility/configuration functions;
 * the static {@code sh(...)}/{@code su(...)} high level APIs using the global {@code Shell} instance;
 * and the lower level API instance methods interacting with the invoking {@code Shell} instance.
 * <p>
 * A {@code Shell} instance consists of a Unix shell process along with 2 additional threads to
 * gobble through STDOUT and STDERR. The process itself and the 2 worker threads are always running
 * in the background as long as the {@code Shell} instance exists unless an error occurs.
 * <p>
 * Tp share shells means the {@code Shell} instance would need a container.
 * Generally, most developers would want to have the {@code Shell} instance shared globally
 * across the application. In that case, the developer can directly subclass the the readily
 * available {@link ShellContainerApp}, or assign {@code com.topjohnwu.superuser.ShellContainerApp}
 * in {@code <application>} of {@code Manifest.xml}, and the setup is all done.
 * If it is impossible to subclass {@link ShellContainerApp}, or for some reason one would
 * want to store the {@code Shell} instance somewhere else, check the documentation of
 * {@link Container} for more info. If no {@link Container} is registered, every shell related methods
 * (including {@link #rootAccess()}) will respawn a new shell process, which is very inefficient.
 * The {@code Shell} class stores a {@link WeakReference} of the registered container
 * so the container could be garbage collected to prevent memory leak if you decide to
 * store {@code Shell} in places like {@link android.app.Activity}.
 * <p>
 * Once a global {@link Container} is registered, use {@link #getShell()} (synchronously) or
 * {@link #getShell(GetShellCallback)} (asynchronously) to get the global {@code Shell} instance.
 * However in most cases, developers do not need a {@code Shell} instance; instead call
 * {@code sh(...)}/{@code su(...)} as they use the global shell and provides a higher level API.
 * One thing worth mentioning: {@code sh(...)} and {@code su(...)} behaves exactly the same, the
 * only difference is that {@code su(...)} will only run if the underlying {@code Shell}
 * is a root shell. Be aware that {@code sh(...)} will still run in a root environment if the
 * global shell is a root shell.
 * <p>
 * When you run a {@link Job} in a background thread or calling {@link Job#enqueue()}, you can
 * access the output reactively by using a more advanced callback with {@link CallbackList}:
 * {@link CallbackList#onAddElement(Object)} will be invoked on the main thread every time the shell
 * outputs a new line.
 * <p>
 * Developers can check the example that came along with the library, it demonstrates many features
 * the library has to offer.
 */

public abstract class Shell extends ShellCompat implements Closeable {

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
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_REDIRECT_STDERR = 0x08;
    
    private static int flags = 0;
    private static WeakReference<Container> weakContainer = new WeakReference<>(null);
    private static Class<? extends Initializer> initClass = null;

    /* **************************************
    * Static utility / configuration methods
    * ***************************************/

    /**
     * Set the container to store the global {@code Shell} instance.
     * <p>
     * Future shell commands using static method APIs will automatically obtain a {@code Shell}
     * from the container with {@link #getShell()} or {@link #getShell(GetShellCallback)}.
     * @param container the container to store the global {@code Shell} instance.
     */
    public static void setContainer(@Nullable Container container) {
        weakContainer = new WeakReference<>(container);
    }

    /**
     * Set a desired {@code Initializer}.
     * @see Initializer
     * @param init the class of the desired initializer.
     *             <strong>If it is a nested class, it MUST be a static nested class!!</strong>
     */
    public static void setInitializer(@NonNull Class<? extends Initializer> init) {
        initClass = init;
    }

    /**
     * Set special flags that controls how {@code Shell} works and how a new {@code Shell} will be
     * constructed.
     * @param flags the desired flags.
     *              Value is either 0 or a combination of {@link #FLAG_NON_ROOT_SHELL},
     *              {@link #FLAG_VERBOSE_LOGGING}, {@link #FLAG_MOUNT_MASTER},
     *              {@link #FLAG_REDIRECT_STDERR}
     */
    public static void setFlags(int flags) {
        Shell.flags = flags;
    }

    /**
     * Get special flags that controls how {@code Shell} works and how a new {@code Shell} will be
     * constructed.
     * @return the flags
     */
    public static int getFlags() {
        return flags;
    }

    /**
     * Set whether enable verbose logging.
     * <p>
     * This is just a handy function to toggle verbose logging with a boolean value.
     * For example: {@code Shell.verboseLogging(BuildConfig.DEBUG)}.
     * @param verbose if true, add {@link #FLAG_VERBOSE_LOGGING} to flags.
     */
    public static void verboseLogging(boolean verbose) {
        if (verbose)
            flags |= FLAG_VERBOSE_LOGGING;
    }

    /**
     * Get a {@code Shell} instance from the global container.
     * If the global container is not set, or the container has not contained any {@code Shell} yet,
     * it will call {@link #newInstance()} to construct a new {@code Shell}.
     * @see #newInstance()
     * @return a {@code Shell} instance
     */
    @NonNull
    public static Shell getShell() {
        Shell shell = getGlobalShell();

        if (shell == null) {
            shell = newInstance();
            Container container = weakContainer.get();
            if (container != null)
                container.setShell(shell);
        }

        return shell;
    }

    /**
     * Get a {@code Shell} instance from the global container and call a callback.
     * When the global container is set and contains a shell, the callback will be called immediately,
     * or else it will queue a new asynchronous task to call {@link #newInstance()} and the callback.
     * @param callback called when a shell is acquired.
     */
    public static void getShell(@NonNull GetShellCallback callback) {
        Shell shell = getGlobalShell();
        if (shell != null) {
            // If global shell exists, it runs synchronously
            callback.onShell(shell);
        } else {
            // Else we add it to the queue and call the callback when we get a Shell
            AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> callback.onShell(getShell()));
        }
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
     *     <li>Construct a Unix shell by calling {@code sh}. This would not fail in normal
     *     conditions, but should it fails, it will throw {@link NoShellException}</li>
     * </ol>
     * The developer should check the status of the returned {@code Shell} with {@link #getStatus()}
     * since it may return the result of any of the 3 possible methods.
     * @return a new {@code Shell} instance.
     * @throws NoShellException impossible to construct {@code Shell} instance, or initialization
     * failed when using the {@link Initializer} set in {@link #setInitializer(Class)}.
     */
    @NonNull
    public static Shell newInstance() {
        Shell shell = null;

        if (!InternalUtils.hasFlag(FLAG_NON_ROOT_SHELL) && InternalUtils.hasFlag(FLAG_MOUNT_MASTER)) {
            // Try mount master
            try {
                shell = Factory.createShell("su", "--mount-master");
                if (!shell.isRoot())
                    shell = null;
            } catch (IOException e) {
                // Shell initialize failed
                InternalUtils.stackTrace(e);
                shell = null;
            }
        }

        if (shell == null && !InternalUtils.hasFlag(FLAG_NON_ROOT_SHELL)) {
            // Try normal root shell
            try {
                shell = Factory.createShell("su");
                if (!shell.isRoot())
                    shell = null;
            } catch (IOException e) {
                // Shell initialize failed
                InternalUtils.stackTrace(e);
                shell = null;
            }
        }

        if (shell == null) {
            // Try normal non-root shell
            try {
                shell = Factory.createShell("sh");
            } catch (IOException e) {
                // Shell initialize failed
                InternalUtils.stackTrace(e);
                throw new NoShellException();
            }
        }

        initShell(shell);

        return shell;
    }

    /**
     * Construct a new {@code Shell} instance with provided commands.
     * @param commands commands that will be passed to {@link Runtime#exec(String[])} to create
     *                 a new {@link Process}.
     * @return a new {@code Shell} instance.
     * @throws NoShellException the provided command cannot create a {@code Shell} instance, or
     * initialization failed when using the {@link Initializer} set in {@link #setInitializer(Class)}.
     */
    @NonNull
    public static Shell newInstance(String... commands) {
        try {
            Shell shell = Factory.createShell(commands);
            initShell(shell);
            return shell;
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
            throw new NoShellException();
        }
    }

    /**
     * Return whether the global shell has root access.
     * @return {@code true} if the global shell has root access.
     */
    public static boolean rootAccess() {
        return getShell().isRoot();
    }

    /* ************
     * Static APIs
     * ************/

    public static Job su(String... commands) {
        Shell shell = getShell();
        if (shell.isRoot())
            return sh(commands);
        return new NOPJob();
    }

    public static Job sh(String... commands) {
        return newJob(getShell(), commands);
    }

    public static Job su(InputStream in) {
        Shell shell = getShell();
        if (shell.isRoot())
            return sh(in);
        return new NOPJob();
    }

    public static Job sh(InputStream in) {
        return newJob(getShell(), in);
    }

    /* **********************
     * Private helper methods
     * ***********************/

    private static Job newJob(Shell shell, String... commands) {
        return shell.newJob(commands).to(NOPList.getInstance());
    }

    private static Job newJob(Shell shell, InputStream in) {
        return shell.newJob(in).to(NOPList.getInstance());
    }

    private static void initShell(Shell shell) {
        Initializer init = null;
        if (initClass != null) {
            try {
                // Force enabling the default constructor as it might be private
                Constructor<? extends Initializer> ic = initClass.getDeclaredConstructor();
                ic.setAccessible(true);
                init = ic.newInstance();
            } catch (Exception e) {
                InternalUtils.stackTrace(e);
            }
        } else if (initializer != null) {
            init = initializer;
        }
        if (init == null)
            init = new Initializer();
        if (!init.init(shell))
            throw new NoShellException();
    }

    private static Shell getGlobalShell() {
        Shell shell = null;
        Container container = weakContainer.get();

        if (container != null)
            shell = container.getShell();

        if (shell != null && !shell.isAlive())
            shell = null;

        return shell;
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
     * Execute a {@code Task} with the shell.
     * @param task the desired task.
     */
    public abstract void execTask(@NonNull Task task) throws IOException;

    public abstract Job newJob(String... cmds);

    public abstract Job newJob(InputStream in);

    /**
     * Get the status of the shell.
     * @return the status of the shell.
     *         Value is either {@link #UNKNOWN}, {@link #NON_ROOT_SHELL}, {@link #ROOT_SHELL}, or
     *         {@link #ROOT_MOUNT_MASTER}
     */
    public abstract int getStatus();

    /**
     * @return whether the shell is a root shell.
     */
    public boolean isRoot() {
        return getStatus() >= ROOT_SHELL;
    }

    /* **********
    * Subclasses
    * ***********/

    /**
     * A task that can be executed by a shell with the method {@link #execTask(Task)}.
     */
    public interface Task {
        /**
         * This method will be called when a task is executed by a shell.
         * @param stdin the STDIN of the shell.
         * @param stdout the STDOUT of the shell.
         * @param stderr the STDERR of the shell.
         * @throws IOException I/O errors when doing operations on stdin/out/err
         */
        void run(OutputStream stdin, InputStream stdout, InputStream stderr) throws IOException;
    }

    public abstract static class Result {

        public abstract List<String> getOut();

        public abstract List<String> getErr();

        public abstract int getCode();
    }

    public interface ResultCallback {
        void onResult(Result out);
    }

    public abstract static class Job {
        public abstract Job to(List<String> stdout);
        public abstract Job to(List<String> stdout, List<String> stderr);
        public abstract Job onResult(ResultCallback cb);
        public abstract Result exec();
        public abstract void enqueue();
    }

    /**
     * The container to store the global {@code Shell} instance.
     * <p>
     * Create a volatile field for storing the {@code Shell} instance, implement {@link #getShell()}
     * and {@link #setShell(Shell)} to expose the new field, and don't forget to register yourself
     * in the constructor by calling {@link #setContainer(Container)} with {@code this}.
     */
    public interface Container {
        /**
         * @return the {@code Shell} instance stored in the implementing class.
         */
        @Nullable
        Shell getShell();

        /**
         * @param shell replaces the instance stored in the implementing class.
         */
        void setShell(@Nullable Shell shell);
    }

    /**
     * The initializer when a new {@code Shell} is constructed.
     * <p>
     * This is an advanced feature. If you need to run specific operations when a new {@code Shell}
     * is constructed, subclass this class, add your own implementation, and register it with
     * {@link #setInitializer(Class)}.
     * The concept is a bit like {@code .bashrc}: a specific script/command will run when the shell
     * starts up. {@link #onInit(Context, Shell)} will be called as soon as the {@code Shell} is
     * constructed and tested as a valid shell.
     * <p>
     * Note: If you want the initializer to run in a BusyBox environment, call
     * {@link BusyBox#setup(Context)} or assign {@link BusyBox#BB_PATH} before any shell will
     * be constructed.
     * <p>
     * An initializer will be constructed and the callbacks will be invoked each time a new
     * {@code Shell} is created. A {@code Context} will be passed to the callbacks, use it to
     * access resources within the APK (e.g. shell scripts).
     */
    public static class Initializer extends InitializerCompat {

        /**
         * Called when a new shell is constructed.
         * Do NOT call the super method; the default implementation is only for backwards compatibility.
         * @param context the application context.
         * @param shell the newly constructed shell.
         * @return {@code false} when the initialization fails, otherwise {@code true}
         */
        public boolean onInit(Context context, @NonNull Shell shell) {
            return super.onInit(context, shell);
        }

        private boolean init(Shell shell) {
            if (shell.isRoot())
                BusyBox.init(shell);
            return onInit(InternalUtils.getContext(), shell);
        }
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
}
