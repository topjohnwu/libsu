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

import android.app.Application;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.topjohnwu.superuser.internal.Factory;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class providing an API to an interactive (root) shell.
 * <p>
 * This class can be put into 3 categories: the static methods, which are some utility functions;
 * the methods in {@link Sync} and {@link Async}, which are high level APIs using the global
 * {@code Shell} instance; the instance methods, which are low level APIs interacting with the invoking
 * {@code Shell} instance.
 * <p>
 * A {@code Shell} instance consists of a Unix shell process along with 2 additional threads to
 * gobble through STDOUT and STDERR. The process itself and the 2 worker threads are always running
 * in the background as long as the {@code Shell} instance exists unless an error occurs.
 * <p>
 * Creating a new root shell is an expensive operation, so the best practice for any root app is to
 * create a single shell session and share it across the application. This class is designed with
 * this concept in mind, and provides a very easy way to share shell sessions. One of the challenges
 * in sharing a shell session is synchronization, as a single shell can only do tasks serially. This
 * class does all the heavy lifting under-the-hood, providing consistent output and synchronization.
 * In multi-thread environments or using the asynchronous APIs in this class, the shell and
 * all callbacks are all handled with concurrency in mind, so all tasks will be queued internally.
 * <p>
 * A global shell means it needs a container to store the {@code Shell} instance.
 * Generally, most developers would want to have the {@code Shell} instance shared globally
 * across the application. In that case, the developer can directly subclass the the readily
 * available {@link ContainerApp} (which extends {@link Application}) and the setup is all done.
 * If it is impossible to subclass {@link ContainerApp}, or for some reason one would
 * want to store the {@code Shell} instance somewhere else, the developer would need to manually
 * create a volatile field in the target class for storing the {@code Shell} instance, implement the
 * {@link Container} interface to expose the new field, and finally register the container as a
 * global container in its constructor by calling {@link #setContainer(Container)}. If no
 * {@link Container} is setup, every shell related methods (including {@link #rootAccess()})
 * will respawn a new shell process, which is very inefficient.
 * <p>
 * The reason behind this design is to let the {@code Shell} instance live along with the life
 * cycle of the target class. The {@code Shell} class statically stores a {@link WeakReference}
 * of the registered container, which means the container could be garbage collected if applicable.
 * For example, one decides to store the {@code Shell} instance in an {@link android.app.Activity}.
 * When the {@code Activity} is constructed, it shall register itself as the global
 * {@link Container}. All root commands will be executed via the single {@code Shell}
 * instance stored in a field of the activity as long as the activity is alive. When the activity
 * is terminated (e.g. the user leaves the activity), the garbage collector will do its job and close
 * the root shell, and at the same time the reference to the container will also be removed.
 * <p>
 * Once a global {@link Container} is registered, use {@link #getShell()} (synchronously) or
 * {@link #getShell(GetShellCallback)} (asynchronously) to get the global {@code Shell} instance.
 * However in most cases, developers do not need to get a {@code Shell} instance; instead use
 * the helper methods in {@link Sync} and {@link Async}, as they all use the global
 * shell and provides a higher level API.
 * One thing worth mentioning: {@code sh(...)} and {@code su(...)} behaves exactly the same, the
 * only difference is that {@code su(...)} methods will only run if the underlying {@code Shell}
 * is a root shell. This also means that {@code sh(...)} will run in a root environment if the
 * global shell is actually a root shell.
 * <p>
 * The {@link Async} class hosts all asynchronous helper methods, and they are all
 * guaranteed to return immediately: it will get shell asynchronously and run commands
 * asynchronously. All asynchronous tasks are queued and executed with Android's native
 * {@link AsyncTask#THREAD_POOL_EXECUTOR}, so threads and execution is managed along with all other
 * {@link AsyncTask} by the system.
 * Asynchronous APIs are useful when the developer just wants to run commands and does not care the
 * output at all ({@link Async#su(String...)}). One can register a {@link Async.Callback}
 * when the asynchronous task is done, the outputs will be passed to the callback. Furthermore,
 * since the output is updated asynchronously, a more advanced callback could be done with the help
 * of {@link CallbackList}: {@link CallbackList#onAddElement(Object)} will be invoked every time a
 * new line is outputted.
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
     * Note: This flag affects all methods in {@link Sync} {@link Async} except:
     * <ul>
     *     <li>{@link Sync#sh(List, List, String...)}</li>
     *     <li>{@link Sync#su(List, List, String...)}</li>
     *     <li>{@link Sync#loadScript(List, List, InputStream)}</li>
     *     <li>{@link Async#sh(List, List, String...)}</li>
     *     <li>{@link Async#sh(List, List, Shell.Async.Callback, String...)}</li>
     *     <li>{@link Async#su(List, List, String...)}</li>
     *     <li>{@link Async#su(List, List, Shell.Async.Callback, String...)}</li>
     *     <li>{@link Async#loadScript(List, List, InputStream)}</li>
     *     <li>{@link Async#loadScript(List, List, Shell.Async.Callback, InputStream)}</li>
     * </ul>
     * <p>
     * Constant value {@value}.
     */
    public static final int FLAG_REDIRECT_STDERR = 0x08;

    /**
     * The status of the shell
     */
    protected int status;
    
    private static int flags = 0;
    private static WeakReference<Container> weakContainer = new WeakReference<>(null);
    private static Initializer initializer = new Initializer();

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
     * @param init the desired initializer.
     */
    public static void setInitializer(@NonNull Initializer init) {
        initializer = init;
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
            AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                Shell s = getShell();
                callback.onShell(s);
            });
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
     * @throws NoShellException impossible to construct {@code Shell} instance.
     */
    @NonNull
    public static Shell newInstance() {
        Shell shell = null;

        if (!InternalUtils.hasFlag(FLAG_NON_ROOT_SHELL) && InternalUtils.hasFlag(FLAG_MOUNT_MASTER)) {
            // Try mount master
            try {
                shell = Factory.createShell("su", "--mount-master");
                if (shell.status == ROOT_SHELL)
                    shell.status = ROOT_MOUNT_MASTER;
                else
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
                if (shell.status != ROOT_SHELL)
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
     * @throws NoShellException the provided command cannot create a new Unix shell.
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
        return getShell().status > NON_ROOT_SHELL;
    }

    /**
     * High level API for synchronous operations
     */
    public final static class Sync {

        private Sync() {}

        /* ************************************
        * Global static synchronous shell APIs
        * *************************************/

        /**
         * Equivalent to {@code sh(new ArrayList<String>(), commands)} with the new ArrayList
         * returned when all commands are done.
         * @return the result of the commands.
         */
        @NonNull
        public static ArrayList<String> sh(@NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            sh(result, commands);
            return result;
        }

        /**
         * Equivalent to <pre><code>sh(output, REDIRECT_STDERR &#63; output : null, commands).</code></pre>
         * @param output if {@link #FLAG_REDIRECT_STDERR} is set, STDERR outputs will also be stored here.
         */
        public static void sh(List<String> output, @NonNull String... commands) {
            sh(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        }

        /**
         * Get a shell from {@link #getShell()} and call {@link #run(List, List, String...)}.
         * @see #run(List, List, String...)
         */
        public static void sh(List<String> output, List<String> error, @NonNull String... commands) {
            global_run_wrapper(false, output, error, commands);
        }

        /* *****************************************
        * Global static synchronous root shell APIs
        * ******************************************/

        /**
         * Equivalent to {@link #sh(String...)} with root access check before running.
         */
        @NonNull
        public static ArrayList<String> su(@NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            su(result, commands);
            return result;
        }

        /**
         * Equivalent to {@link #sh(List, String...)} with root access check before running.
         */
        public static void su(List<String> output, @NonNull String... commands) {
            su(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        }

        /**
         * Equivalent to {@link #sh(List, List, String...)} with root access check before running.
         */
        public static void su(List<String> output, List<String> error, @NonNull String... commands) {
            global_run_wrapper(true, output, error, commands);
        }

        /* *****************************************
        * Global static loadScript synchronous APIs
        * ******************************************/

        /**
         * Equivalent to {@code loadScript(new ArrayList<String>(), in)} with the new ArrayList
         * returned after the script finish running.
         * @return the result of the script loaded from the InputStream.
         */
        @NonNull
        public static ArrayList<String> loadScript(@NonNull InputStream in) {
            ArrayList<String> result = new ArrayList<>();
            loadScript(result, in);
            return result;
        }

        /**
         * Equivalent to <pre><code>loadScript(output, REDIRECT_STDERR &#63; output : null, in).</code></pre>
         * @param output if {@link #FLAG_REDIRECT_STDERR} is set, STDERR outputs will also be stored here.
         */
        public static void loadScript(List<String> output, @NonNull InputStream in) {
            loadScript(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, in);
        }

        /**
         * Get a shell from {@link #getShell()} and call {@link #loadInputStream(List, List, InputStream)}.
         * @see #loadInputStream(List, List, InputStream)
         */
        public static void loadScript(List<String> output, List<String> error, @NonNull InputStream in) {
            getShell().loadInputStream(output, error, in);
        }
    }

    /**
     * High level API for asynchronous operations
     */
    public final static class Async {

        private Async() {}

        /**
         * The callback when an asynchronous shell operation is done.
         * <p>
         * When an asynchronous shell operation is done, it will pass over the result of the output
         * to {@link #onTaskResult(List, List)}. If both outputs are null, then the callback will
         * not be called.
         * <p>
         * Similar to {@link CallbackList}, when the asynchronous operation is initialized on the
         * main thread, the callback will also be run on the main thread. So updating the UI
         * in the callbacks are allowed.
         * <p>
         * The two lists passed to the callback are wrapped with
         * {@link Collections#synchronizedList(List)}, so the developer can iterate the list without
         * worrying about {@link java.util.ConcurrentModificationException}.
         */
        public interface Callback {
            /**
             * The method that will be called when asynchronous shell operation is done.
             * If {@code out == err}, then {@code err} will be {@code null}.
             * This means if {@link #FLAG_REDIRECT_STDERR} is set, the output of STDERR would be
             * stored in {@code out}, {@code err} will be {@code null}, unless explicitly passed
             * a different list to store STDERR while request.
             * @param out the list that stores the output of STDOUT.
             * @param err the list that stores the output of STDERR.
             */
            void onTaskResult(@Nullable List<String> out, @Nullable List<String> err);
        }

        /* *************************************
        * Global static asynchronous shell APIs
        * **************************************/

        /**
         * Equivalent to {@code sh(null, null, null, commands)}.
         */
        public static void sh(@NonNull String... commands) {
            sh(null, null, null, commands);
        }

        /**
         * Equivalent to <pre><code>output = new ArrayList&#60;String&#62;(); sh(output, REDIRECT_STDERR &#63; output : null, callback, commands).</code></pre>
         * <p>
         * This method is useful if you only need a callback after the commands are done.
         */
        public static void sh(Callback callback, @NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            sh(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, commands);
        }

        /**
         * Equivalent to <pre><code>sh(output, REDIRECT_STDERR &#63; output : null, null, commands).</code></pre>
         */
        public static void sh(List<String> output, @NonNull String... commands) {
            sh(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, null, commands);
        }

        /**
         * Equivalent to {@code sh(output, error, null, commands)}.
         */
        public static void sh(List<String> output, List<String> error, @NonNull String... commands) {
            sh(output, error, null, commands);
        }

        /**
         * Get a shell with {@link #getShell(GetShellCallback)} and call
         * {@link #run(List, List, Shell.Async.Callback, String...)}.
         * @see #run(List, List, Shell.Async.Callback, String...)
         */
        public static void sh(List<String> output, List<String> error, Callback callback, @NonNull String... commands) {
            global_run_async_wrapper(false, output, error, callback, commands);
        }

        /* ******************************************
        * Global static asynchronous root shell APIs
        * *******************************************/

        /**
         * Equivalent to {@link #sh(String...)} with root access check before running.
         */
        public static void su(@NonNull String... commands) {
            su(null, null, null, commands);
        }

        /**
         * Equivalent to {@link #sh(Shell.Async.Callback, String...)} with root access check before running.
         */
        public static void su(Callback callback, @NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            su(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, commands);
        }

        /**
         * Equivalent to {@link #sh(List, String...)} with root access check before running.
         */
        public static void su(List<String> output, @NonNull String... commands) {
            su(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, null, commands);
        }

        /**
         * Equivalent to {@link #sh(List, List, String...)} with root access check before running.
         */
        public static void su(List<String> output, List<String> error, @NonNull String... commands) {
            su(output, error, null, commands);
        }

        /**
         * Equivalent to {@link #sh(List, List, Callback, String...)} with root access check before running.
         */
        public static void su(List<String> output, List<String> error, Callback callback,
                              @NonNull String... commands) {
            global_run_async_wrapper(true, output, error, callback, commands);
        }

        /* ******************************************
        * Global static loadScript asynchronous APIs
        * *******************************************/

        /**
         * Equivalent to {@code loadScript(null, null, null, in)}.
         */
        public static void loadScript(InputStream in) {
            loadScript(null, null, null, in);
        }

        /**
         * Equivalent to <pre><code>output = new ArrayList&#60;String&#62;(); loadScript(output, REDIRECT_STDERR &#63; output : null, callback, in).</code></pre>
         * <p>
         * This method is useful if you only need a callback after the script finish running.
         * <p>
         * Note: when the {@link Callback#onTaskResult(List, List)} of the callback is called,
         * the second parameter will always be null. If {@link #FLAG_REDIRECT_STDERR} is set, the
         * output of STDERR will be stored in the first parameter.
         */
        public static void loadScript(Callback callback, @NonNull InputStream in) {
            ArrayList<String> result = new ArrayList<>();
            loadScript(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, in);
        }

        /**
         * Equivalent to <pre><code>loadScript(output, REDIRECT_STDERR &#63; output : null, in).</code></pre>
         */
        public static void loadScript(List<String> output, @NonNull InputStream in) {
            loadScript(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, in);
        }

        /**
         * Equivalent to {@code loadScript(output, error, null, in)}.
         */
        public static void loadScript(List<String> output, List<String> error,
                                      @NonNull InputStream in) {
            loadScript(output, error, null, in);
        }

        /**
         * Get a shell with {@link #getShell(GetShellCallback)} and call
         * {@link #loadInputStream(List, List, Shell.Async.Callback, InputStream)}.
         * @see #loadInputStream(List, List, Shell.Async.Callback, InputStream)
         */
        public static void loadScript(List<String> output, List<String> error, Callback callback,
                                      @NonNull InputStream in) {
            getShell().loadInputStream(output, error, callback, in);
        }
    }

    /* ***************
    * Non-static APIs
    * ****************/

    /**
     * Get the status of the shell.
     * @return the status of the shell.
     *         Value is either {@link #UNKNOWN}, {@link #NON_ROOT_SHELL}, {@link #ROOT_SHELL},
     *         {@link #ROOT_MOUNT_MASTER}
     */
    public int getStatus() {
        return status;
    }

    /**
     * Return whether the {@code Shell} is still alive.
     * @return {@code true} if the {@code Shell} is still alive.
     */
    public abstract boolean isAlive();

    /**
     * Synchronously run commands and stores outputs to the two lists.
     * @param output the list to store STDOUT outputs.
     * @param error the list to store STDERR outputs.
     * @param commands the commands to run in the shell.
     */
    public abstract void run(List<String> output, List<String> error, @NonNull String... commands);

    /**
     * Asynchronously run commands, stores outputs to the two lists, and call the callback when
     * all commands are ran and the outputs are done.
     * @param output the list to store STDOUT outputs.
     * @param error the list to store STDERR outputs.
     * @param callback the callback when all commands are ran and the outputs are done.
     * @param commands the commands to run in the shell.
     */
    public abstract void run(List<String> output, List<String> error,
                             Async.Callback callback, @NonNull String... commands);

    /**
     * Synchronously load an input stream to the shell and stores outputs to the two lists.
     * <p>
     * This command is useful for loading a script stored in the APK. An InputStream can be opened
     * from assets with {@link android.content.res.AssetManager#open(String)} or from raw resources
     * with {@link android.content.res.Resources#openRawResource(int)}.
     * @param output the list to store STDOUT outputs.
     * @param error the list to store STDERR outputs.
     * @param in the InputStream to load
     */
    public abstract void loadInputStream(List<String> output, List<String> error,
                                         @NonNull InputStream in);

    /**
     * Asynchronously load an input stream to the shell, stores outputs to the two lists, and call
     * the callback when the InputStream is loaded and the outputs are done.
     * <p>
     * This command is useful for loading a script stored in the APK. An InputStream can be opened
     * from assets with {@link android.content.res.AssetManager#open(String)} or from raw resources
     * with {@link android.content.res.Resources#openRawResource(int)}.
     * @param output the list to store STDOUT outputs.
     * @param error the list to store STDERR outputs.
     * @param callback the callback when the InputStream is loaded and the outputs are done.
     * @param in the InputStream to load
     */
    public abstract void loadInputStream(List<String> output, List<String> error,
                                Async.Callback callback, @NonNull InputStream in);

    /* **********************
    * Private helper methods
    * ***********************/

    private static void initShell(Shell shell) {
        initializer.onShellInit(shell);
        if (shell.status >= ROOT_SHELL)
            initializer.onRootShellInit(shell);
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

    private static void global_run_wrapper(boolean root, List<String> output,
                                           List<String> error, String... commands) {
        Shell shell = getShell();
        if (root && shell.status == NON_ROOT_SHELL)
            return;
        shell.run(output, error, commands);
    }

    private static void global_run_async_wrapper(boolean root, List<String> output,
                                                 List<String> error, Async.Callback callback,
                                                 String... commands) {
        getShell(shell -> {
            if (root && shell.status == NON_ROOT_SHELL)
                return;
            shell.run(output, error, callback, commands);
        });
    }

    /**
     * The container to store the global {@code Shell} instance.
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
     * A subclass of {@link Application} that implements {@link Container}.
     */
    public static class ContainerApp extends Application implements Container {

        /**
         * The actual field to save the global {@code Shell} instance.
         */
        protected volatile Shell mShell;

        /**
         * Set the {@code ContainerApp} as the global container as soon as it is constructed.
         */
        public ContainerApp() {
            setContainer(this);
        }

        @Nullable
        @Override
        public Shell getShell() {
            return mShell;
        }

        @Override
        public void setShell(@Nullable Shell shell) {
            mShell = shell;
        }
    }

    /**
     * The initializer when a new {@code Shell} is constructed.
     * <p>
     * This is an advanced feature. If you need to run specific operations when a new {@code Shell}
     * is constructed, subclass this class, add your own implementation, and register it with
     * {@link #setInitializer(Initializer)}.
     * The concept is a bit like {@code .bashrc} a specific script/command will run when the shell
     * starts up.
     * A {@code Shell} instance will be passed to the two callbacks, please directly call the low level
     * APIs on the instance. <strong>DO NOT</strong> call the methods in {@link Shell.Sync} or
     * {@link Shell.Async}, since the global shell is not setup yet, calling the high level
     * APIs will end up in an infinite loop of creating new {@code Shell} and calling the initializer.
     * <p>
     * The {@link #onShellInit(Shell)} will be called as soon as the {@code Shell} is constructed
     * and tested as a valid shell. {@link #onRootShellInit(Shell)} will only be called after the
     * {@code Shell} passes the internal root shell test. In short, a non-root shell will only
     * be initialized with {@link #onShellInit(Shell)}, while a root shell will be initialized with
     * both {@link #onShellInit(Shell)} and {@link #onRootShellInit(Shell)}.
     */
    public static class Initializer {
        /**
         * Called when a new shell is constructed.
         * The default implementation is NOP.
         * @param shell the newly constructed shell
         */
        public void onShellInit(@NonNull Shell shell) {}

        /**
         * Called when a new shell has passed the internal root tests.
         * The default implementation is NOP.
         * @param shell the newly constructed shell that passes internal root tests.
         */
        public void onRootShellInit(@NonNull Shell shell) {}

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
