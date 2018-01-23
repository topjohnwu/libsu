package com.topjohnwu.superuser;

import android.os.AsyncTask;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by topjohnwu on 2018/1/19.
 */

public class Shell implements Closeable {

    public static final int UNKNOWN = -1;
    public static final int NON_ROOT_SHELL = 0;
    public static final int ROOT_SHELL = 1;
    public static final int ROOT_MOUNT_MASTER = 2;
    public static final int FLAG_NON_ROOT_SHELL = 0x01;
    public static final int FLAG_MOUNT_MASTER = 0x02;
    public static final int FLAG_VERBOSE_LOGGING = 0x04;
    public static final int FLAG_REDIRECT_STDERR = 0x08;

    static int flags = 0;

    private static final String INTAG = "SHELL_IN";
    private static final String TAG = "LIBSU";
    private static WeakReference<ShellContainer> weakContainer = new WeakReference<>(null);
    private static ShellInitializer initializer = new ShellInitializer();

    final Process process;
    final OutputStream STDIN;
    final InputStream STDOUT;
    final InputStream STDERR;
    final ReentrantLock lock;

    private int status;
    private CharSequence token;
    private StreamGobbler outGobbler;
    private StreamGobbler errGobbler;

    private Shell(String... cmd) throws IOException {
        lock = new ReentrantLock();
        status = UNKNOWN;
        token = Utils.genRandomAlphaNumString(32);

        process = Runtime.getRuntime().exec(cmd);
        STDIN = process.getOutputStream();
        STDOUT = process.getInputStream();
        STDERR = process.getErrorStream();
        outGobbler = new StreamGobbler(STDOUT, token);
        errGobbler = new StreamGobbler(STDERR, token);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    /* **************************************
    * Static utility / configuration methods
    * ***************************************/

    public static void setGlobalContainer(ShellContainer container) {
        weakContainer = new WeakReference<>(container);
    }

    public static void setInitializer(ShellInitializer init) {
        initializer = init;
    }

    public static void removeInitializer() {
        initializer = new ShellInitializer();
    }

    public static void setFlags(int flags) {
        Shell.flags = flags;
    }

    public static void addFlags(int flags) {
        Shell.flags |= flags;
    }

    public static void removeFlags(int flags) {
        Shell.flags &= (~flags);
    }

    public static void enableVerboseLogging(boolean verbose) {
        if (verbose)
            addFlags(FLAG_VERBOSE_LOGGING);
    }

    public static Shell getShell() throws NoShellException {
        Shell shell = getGlobalShell();

        if (shell == null) {
            shell = newShell();
            ShellContainer container = weakContainer.get();
            if (container != null)
                container.setShell(shell);
        }

        return shell;
    }

    public static boolean rootAccess() {
        try {
            return getShell().status > 0;
        } catch (NoShellException e) {
            return false;
        }
    }

    /* ************************
    * Global shell static APIs
    * *************************/

    public static ArrayList<String> sh(String... commands) {
        ArrayList<String> res = new ArrayList<>();
        sh(res, commands);
        return res;
    }

    public static void sh(List<String> output, String... commands) {
        sh(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
    }

    public static void sh(List<String> output, List<String> error, String... commands) {
        global_run_wrapper(false, output, error, commands);
    }

    public static PoolThread sh_async(final List<String> output, final String... commands) {
        return sh_async(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
    }

    public static PoolThread sh_async(List<String> output, List<String> error, String... commands) {
        return global_run_async_wrapper(false, output, error, commands);
    }

    public static void sh_raw(String... commands) {
        global_run_raw_wrapper(false, commands);
    }

    /* *****************************
    * Global root shell static APIs
    * ******************************/

    public static ArrayList<String> su(String... commands) {
        ArrayList<String> res = new ArrayList<>();
        su(res, commands);
        return res;
    }

    public static void su(List<String> output, String... commands) {
        su(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
    }

    public static void su(List<String> output, List<String> error, String... commands) {
        global_run_wrapper(true, output, error, commands);
    }

    public static PoolThread su_async(final List<String> output, final String... commands) {
        return su_async(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
    }

    public static PoolThread su_async(List<String> output, List<String> error, String... commands) {
        return global_run_async_wrapper(true, output, error, commands);
    }

    public static void su_raw(String... commands) {
        global_run_raw_wrapper(true, commands);
    }

    /* ***************
    * Non-static APIs
    * ****************/

    @Override
    public void close() throws IOException {
        status = UNKNOWN;
        outGobbler.terminate();
        errGobbler.terminate();
        STDIN.close();
        STDERR.close();
        STDOUT.close();
        process.destroy();
    }

    public int getStatus() {
        return status;
    }

    public void run(String... commands) {
        run(null, null, commands);
    }

    public void run(final List<String> output, final List<String> error, final String... commands) {
        run_sync_output(output, error, new Runnable() {
            @Override
            public void run() {
                run_commands(output != null, error != null, commands);
            }
        });
    }

    public PoolThread run_async(final List<String> output, final List<String> error, final String... commands) {
        return run_async_output(output, error, new Runnable() {
            @Override
            public void run() {
                run_commands(output != null, error != null, commands);
            }
        });
    }

    public void run_raw(final String... commands) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                run_commands(false, false, commands);
            }
        });
    }

    public void loadInputStream(InputStream in) {
        loadInputStream(null, null, in);
    }

    public void loadInputStream(List<String> output, List<String> error, final InputStream in) {
        run_sync_output(output, error, new LoadInputStream(in));
    }

    public void loadInputStreamAsync(InputStream in) {
        loadInputStreamAsync(null, null, in);
    }

    public void loadInputStreamAsync(List<String> output, List<String> error, final InputStream in) {
        run_async_output(output, error, new LoadInputStream(in));
    }

    /* *****************************
    * Actual implementation details
    * ******************************/

    public boolean isAlive() {
        // If status is unknown, it is not alive
        if (status < 0)
            return false;
        // If some threads are holding the lock, it is still alive
        if (lock.isLocked())
            return true;
        try {
            process.exitValue();
            // Process is dead, start new shell
            return false;
        } catch (IllegalThreadStateException e) {
            // This should be the expected result
            return true;
        }
    }

    public static Shell newShell() throws NoShellException {
        Shell shell = null;

        if (!Utils.hasFlag(FLAG_NON_ROOT_SHELL) && Utils.hasFlag(FLAG_MOUNT_MASTER)) {
            // Try mount master
            try {
                Utils.log(TAG, "su --mount-master");
                shell = new Shell("su", "--mount-master");
                shell.testShell();
                shell.testRootShell();
                shell.status = ROOT_MOUNT_MASTER;
                initializer.onShellInit(shell);
                initializer.onRootShellInit(shell);
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
                shell = null;
            }
        }

        if (shell == null && !Utils.hasFlag(FLAG_NON_ROOT_SHELL)) {
            // Try normal root shell
            try {
                Utils.log(TAG, "su");
                shell = new Shell("su");
                shell.testShell();
                shell.testRootShell();
                shell.status = ROOT_SHELL;
                initializer.onShellInit(shell);
                initializer.onRootShellInit(shell);
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
                shell = null;
            }
        }

        if (shell == null) {
            // Try normal non-root shell
            try {
                Utils.log(TAG, "sh");
                shell = new Shell("sh");
                shell.testShell();
                shell.status = NON_ROOT_SHELL;
                initializer.onShellInit(shell);
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
                throw new NoShellException();
            }
        }

        return shell;
    }

    public static Shell newShell(String... commands) throws NoShellException {
        try {
            Shell shell = new Shell(commands);
            shell.testShell();
            shell.status = NON_ROOT_SHELL;
            initializer.onShellInit(shell);
            try {
                shell.testRootShell();
                shell.status = ROOT_SHELL;
                initializer.onRootShellInit(shell);
            } catch (IOException ignored) {}
            return shell;
        } catch (IOException e) {
            Utils.stackTrace(e);
            throw new NoShellException();
        }
    }

    private static Shell getGlobalShell() {
        Shell shell = null;
        ShellContainer container = weakContainer.get();

        if (container != null)
            shell = container.getShell();

        if (shell != null && !shell.isAlive())
            shell = null;

        return shell;
    }

    private static void global_run_wrapper(boolean root, List<String> output,
                                           List<String> error, String... commands) {
        try {
            Shell shell = getShell();
            if (root && shell.status == NON_ROOT_SHELL)
                return;
            shell.run(output, error, commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }
    }

    private static PoolThread global_run_async_wrapper(final boolean root, final List<String> output,
                                                       final List<String> error, final String... commands) {
        try {
            Shell shell = getShell();
            if (root && shell.status == NON_ROOT_SHELL)
                return null;
            return shell.run_async(output, error, commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
            return null;
        }
    }

    private static void global_run_raw_wrapper(boolean root, String... commands) {
        try {
            Shell shell = getShell();
            if (root && shell.status == NON_ROOT_SHELL)
                return;
            shell.run_raw(commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }
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
        Utils.log(TAG, "run_commands");
        try {
            for (String command : commands) {
                STDIN.write((command + suffix).getBytes("UTF-8"));
                STDIN.flush();
                Utils.log(INTAG, command);
            }
        } catch (IOException e) {
            e.printStackTrace();
            status = UNKNOWN;
        } finally {
            lock.unlock();
        }
    }

    private void run_sync_output(List<String> output, List<String> error, Runnable callback) {
        lock.lock();
        Utils.log(TAG, "run_sync_output");
        try {
            outGobbler.begin(output);
            if (error != null)
                errGobbler.begin(error);
            callback.run();
            byte[] finalize = String.format("echo %s; echo %s >&2\n", token, token)
                    .getBytes("UTF-8");
            STDIN.write(finalize);
            STDIN.flush();
            try {
                outGobbler.waitDone();
                errGobbler.waitDone();
            } catch (InterruptedException ignored) {}
        } catch (IOException e) {
            e.printStackTrace();
            status = UNKNOWN;
        } finally {
            lock.unlock();
        }
    }

    private PoolThread run_async_output(final List<String> output, final List<String> error, final Runnable callback) {
        Utils.log(TAG, "run_async_output");
        return new PoolThread() {
            @Override
            void run() {
                run_sync_output(output, error, callback);
            }
        }.start();
    }

    private class LoadInputStream implements Runnable {

        private InputStream in;

        LoadInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            Utils.log(TAG, "loadInputStream");
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read;
                byte buffer[] = new byte[4096];
                while ((read = in.read(buffer)) > 0)
                    baos.write(buffer, 0, read);
                in.close();
                // Make sure it flushes the shell
                baos.write("\n".getBytes("UTF-8"));
                baos.writeTo(STDIN);
                STDIN.flush();
                Utils.log(INTAG, baos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
