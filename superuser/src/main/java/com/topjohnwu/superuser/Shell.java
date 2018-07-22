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
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.topjohnwu.superuser.internal.DeprecatedApiShim;
import com.topjohnwu.superuser.internal.Factory;
import com.topjohnwu.superuser.internal.InternalUtils;
import com.topjohnwu.superuser.internal.NOPJob;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
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
    private static Initializer initializer = null;
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
     * @deprecated
     * Set a desired {@code Initializer}.
     * @see Initializer
     * @param init the desired initializer.
     */
    @Deprecated
    public static void setInitializer(@NonNull Initializer init) {
        initializer = init;
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
        return getShell().status > NON_ROOT_SHELL;
    }


    /* ***************************
     * Deprecated High Level APIs
     * ***************************/

    /**
     * @deprecated
     */
    @Deprecated
    public final static class Sync extends DeprecatedApiShim.Sync {
        private Sync() {}
    }

    /**
     * @deprecated
     */
    @Deprecated
    public final static class Async extends DeprecatedApiShim.Async {
        private Async() {}
        public interface Callback {
            void onTaskResult(@Nullable List<String> out, @Nullable List<String> err);
            void onTaskError(@NonNull Throwable err);
        }
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
        return shell.newJob(commands).to(new Output(new ArrayList<>()));
    }

    private static Job newJob(Shell shell, InputStream in) {
        return shell.newJob(in).to(new Output(new ArrayList<>()));
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
    public int getStatus() {
        return status;
    }

    /**
     * @return whether the shell is a root shell.
     */
    public boolean isRoot() {
        return status >= ROOT_SHELL;
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

    public static class Output {

        protected List<String> out;
        protected List<String> err;

        public Output(List<String> outList) {
            this(outList, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? outList : null);
        }

        public Output(List<String> outList, List<String> errList) {
            out = outList;
            err = errList;
        }

        public List<String> getOut() {
            return out;
        }

        public List<String> getErr() {
            return err;
        }
    }

    public interface ResultCallback {
        void onResult(Output out);
    }

    public abstract static class Job {
        public abstract Job to(Output out);
        public abstract Job onResult(ResultCallback cb);
        public abstract Output exec();
        public abstract void enqueue();
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
     * {@link #setInitializer(Class)}.
     * The concept is a bit like {@code .bashrc}: a specific script/command will run when the shell
     * starts up. {@link #onInit(Context, Shell)} will be called as soon as the {@code Shell} is
     * constructed and tested as a valid shell.
     * <p>
     * Note:
     * <ul>
     *     <li>Please directly call the low level APIs on the passed in {@code Shell} instance within
     *     these two callbacks. <strong>DO NOT</strong> use methods in {@link Shell.Sync} or
     *     {@link Shell.Async}. The global shell is not set yet, calling these high level APIs
     *     will end up in an infinite loop of creating new {@code Shell} and calling the initializer.</li>
     *     <li>If you want the initializer to run in a BusyBox environment, call
     *     {@link BusyBox#setup(Context)} or set {@link BusyBox#BB_PATH} before any shell will
     *     be constructed.</li>
     * </ul>
     *
     * <p>
     * An initializer will be constructed and the callbacks will be invoked each time a new
     * {@code Shell} is created. A {@code Context} will be passed to the callbacks, use it to
     * access resources within the APK (e.g. shell scripts).
     */
    public static class Initializer {

        /**
         * @deprecated
         */
        @Deprecated
        public void onShellInit(@NonNull Shell shell) {}

        /**
         * @deprecated
         */
        @Deprecated
        public boolean onShellInit(Context context, @NonNull Shell shell) throws Exception {
            // Backwards compatibility
            onShellInit(shell);
            return true;
        }

        /**
         * @deprecated
         */
        @Deprecated
        public void onRootShellInit(@NonNull Shell shell) {}

        /**
         * @deprecated
         */
        @Deprecated
        public boolean onRootShellInit(Context context, @NonNull Shell shell) throws Exception {
            // Backwards compatibility
            onRootShellInit(shell);
            return true;
        }

        /**
         * Called when a new shell is constructed.
         * Do not call the super method; the default implementation is only for backwards compatibility.
         * @param context the application context.
         * @param shell the newly constructed shell.
         * @return {@code false} when the initialization fails, otherwise {@code true}
         */
        public boolean onInit(Context context, @NonNull Shell shell) {
            try {
                onShellInit(context, shell);
                if (shell.isRoot())
                    onRootShellInit(context, shell);
            } catch (Exception e) {
                return false;
            }
            return true;
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
