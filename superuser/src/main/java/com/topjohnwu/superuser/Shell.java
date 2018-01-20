package com.topjohnwu.superuser;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by topjohnwu on 2018/1/19.
 */

public class Shell implements Closeable {

    public static final int NOT_CHECKED = -1;
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

    public int status;

    final Process process;
    final OutputStream STDIN;
    final InputStream STDOUT;
    final InputStream STDERR;

    private Shell(String... cmd) throws IOException {
        process = Runtime.getRuntime().exec(cmd);
        STDIN = process.getOutputStream();
        STDOUT = process.getInputStream();
        STDERR = process.getErrorStream();
        status = NOT_CHECKED;
    }

    public static void setGlobalContainer(ShellContainer container) {
        weakContainer = new WeakReference<>(container);
    }

    public static void addFlags(int flags) {
        Shell.flags |= flags;
    }

    public static void removeFlags(int flags) {
        Shell.flags &= (~flags);
    }

    public static void setFlags(int flags) {
        Shell.flags = flags;
    }

    public static boolean rootAccess() {
        try {
            return getShell().status > 0;
        } catch (NoShellException e) {
            return false;
        }
    }

    private static void testShell(Shell shell) throws IOException {
        shell.STDIN.write(("echo SHELL_TEST\n").getBytes("UTF-8"));
        shell.STDIN.flush();
        String s = new BufferedReader(new InputStreamReader(shell.STDOUT)).readLine();
        if (TextUtils.isEmpty(s) || !s.contains("SHELL_TEST")) {
            shell.close();
            throw new IOException();
        }
    }

    private static void testRootShell(Shell shell) throws IOException {
        shell.STDIN.write(("id\n").getBytes("UTF-8"));
        shell.STDIN.flush();
        String s = new BufferedReader(new InputStreamReader(shell.STDOUT)).readLine();
        if (TextUtils.isEmpty(s) || !s.contains("uid=0")) {
            shell.close();
            throw new IOException();
        }
    }

    public static Shell newShell() throws NoShellException {
        return getShell(null);
    }

    public static Shell newShell(String... commands) throws NoShellException {
        try {
            Shell shell = new Shell(commands);
            testShell(shell);
            shell.status = NON_ROOT_SHELL;
            try {
                testRootShell(shell);
                shell.status = ROOT_SHELL;
            } catch (IOException ignored) {}
            return shell;
        } catch (IOException e) {
            Utils.stackTrace(e);
            throw new NoShellException();
        }
    }

    public static Shell getShell() throws NoShellException {
        return getShell(weakContainer.get());
    }

    private static Shell getShell(ShellContainer container) throws NoShellException {
        boolean newShell = container == null || container.getShell() == null;

        Shell shell = newShell ? null : container.getShell();

        if (!newShell) {
            newShell = !shell.isAlive();
        }

        if (newShell && !Utils.hasFlag(FLAG_NON_ROOT_SHELL) && Utils.hasFlag(FLAG_MOUNT_MASTER)) {
            // Try mount master
            try {
                Utils.log(TAG, "su --mount-master");
                shell = new Shell("su", "--mount-master");
                testShell(shell);
                testRootShell(shell);
                newShell = false;
                shell.status = ROOT_MOUNT_MASTER;
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
            }
        }

        if (newShell && !Utils.hasFlag(FLAG_NON_ROOT_SHELL)) {
            // Try normal root shell
            try {
                Utils.log(TAG, "su");
                shell = new Shell("su");
                testShell(shell);
                testRootShell(shell);
                newShell = false;
                shell.status = ROOT_SHELL;
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
            }
        }

        if (newShell) {
            // Try normal non-root shell
            try {
                Utils.log(TAG, "sh");
                shell = new Shell("sh");
                testShell(shell);
                shell.status = NON_ROOT_SHELL;
            } catch (IOException e) {
                // Shell initialize failed
                Utils.stackTrace(e);
                throw new NoShellException();
            }
        }

        if (container != null)
            container.setShell(shell);

        return shell;
    }

    public static ArrayList<String> sh(String... commands) {
        ArrayList<String> res = new ArrayList<>();
        sh(res, commands);
        return res;
    }

    public static void sh(Collection<String> output, String... commands) {
        try {
            Shell shell = getShell();
            shell.run(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }

    }

    public static void sh_raw(String... commands) {
        try {
            Shell shell = getShell();
            shell.run_raw(commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }
    }

    public static ArrayList<String> su(String... commands) {
        ArrayList<String> res = new ArrayList<>();
        su(res, commands);
        return res;
    }

    public static void su(Collection<String> output, String... commands) {
        try {
            Shell shell = getShell();
            if (shell.status > 0)
                shell.run(output, Utils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }
    }

    public static void su_raw(String... commands) {
        try {
            Shell shell = getShell();
            if (shell.status > 0)
                shell.run_raw(commands);
        } catch (NoShellException e) {
            Utils.stackTrace(e);
        }
    }

    private void run_raw(boolean stdout, boolean stderr, String... commands) {
        String suffix = (stdout ? "" : " >/dev/null") + (stderr ? "" : " 2>/dev/null") + "\n";
        synchronized (process) {
            try {
                for (String command : commands) {
                    STDIN.write((command + suffix).getBytes("UTF-8"));
                    STDIN.flush();
                    Utils.log(INTAG, command);
                }
            } catch (IOException e) {
                e.printStackTrace();
                process.destroy();
            }
        }
    }

    private void run_sync_output(Collection<String> output, Collection<String> error, Runnable callback) {
        CharSequence token = Utils.genRandomAlphaNumString(32);
        StreamGobbler out, err = null;
        synchronized (process) {
            out = new StreamGobbler(STDOUT, output, token);
            out.start();
            if (error != null) {
                err = new StreamGobbler(STDERR, error, token);
                err.start();
            }
            callback.run();
            try {
                byte[] finalize = String.format("echo %s; echo %s >&2\n", token, token)
                        .getBytes("UTF-8");
                STDIN.write(finalize);
                STDIN.flush();
            } catch (IOException e) {
                process.destroy();
            }
            try {
                out.join();
                if (err != null)
                    err.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void run(String... commands) {
        run(null, null, commands);
    }

    public void run(final Collection<String> output, final Collection<String> error, final String... commands) {
        run_sync_output(output, error, new Runnable() {
            @Override
            public void run() {
                Shell.this.run_raw(output != null, error != null, commands);
            }
        });
    }

    public void run_raw(String... commands) {
        run_raw(false, false, commands);
    }

    public void loadInputStream(InputStream in) {
        loadInputStream(null, null, in);
    }

    public void loadInputStream(Collection<String> output, Collection<String> error, final InputStream in) {
        run_sync_output(output, error, new Runnable() {
            @Override
            public void run() {
                StringBuilder builder = new StringBuilder();
                try {
                    int read;
                    byte buffer[] = new byte[4096];
                    while ((read = in.read(buffer)) > 0)
                        builder.append(new String(buffer, 0, read));
                    STDIN.write(builder.toString().getBytes("UTF-8"));
                    STDIN.flush();
                    Utils.log(INTAG, builder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean isAlive() {
        try {
            process.exitValue();
            // Process is dead, start new shell
            return false;
        } catch (IllegalThreadStateException e) {
            // This should be the expected result
            return true;
        }
    }

    @Override
    public void close() throws IOException {
        STDIN.close();
        STDERR.close();
        STDOUT.close();
        process.destroy();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public interface IShellCallback {
        void onShellOutput(String e);
    }
}
