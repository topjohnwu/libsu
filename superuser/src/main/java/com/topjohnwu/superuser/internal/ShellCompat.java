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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR;
import static com.topjohnwu.superuser.Shell.NON_ROOT_SHELL;

public abstract class ShellCompat {

    protected static Shell.Initializer initializer = null;

    @Deprecated
    public static void setContainer(@Nullable Shell.Container container) {
        Shell.Config.setContainer(container);
    }

    @Deprecated
    public static void setInitializer(@NonNull Class<? extends Shell.Initializer> init) {
        Shell.Config.setInitializer(init);
    }

    @Deprecated
    public static void setFlags(int flags) {
        Shell.Config.setFlags(flags);
    }

    @Deprecated
    public static int getFlags() {
        return Shell.Config.getFlags();
    }

    @Deprecated
    public static void verboseLogging(boolean verbose) {
        Shell.Config.verboseLogging(verbose);
    }

    @Deprecated
    public abstract Throwable run(List<String> outList, List<String> errList, @NonNull String... commands);

    @Deprecated
    public abstract void run(List<String> outList, List<String> errList,
                             Shell.Async.Callback callback, @NonNull String... commands);

    @Deprecated
    public abstract Throwable loadInputStream(List<String> outList, List<String> errList, @NonNull InputStream in);

    @Deprecated
    public abstract void loadInputStream(List<String> outList, List<String> errList,
                                         Shell.Async.Callback callback, @NonNull InputStream in);

    @Deprecated
    public static void setInitializer(@NonNull Shell.Initializer init) {
        initializer = init;
    }

    @Deprecated
    public static class ContainerApp extends com.topjohnwu.superuser.ContainerApp {}

    @Deprecated
    public final static class Sync {

        private Sync() {}

        @NonNull
        public static ArrayList<String> sh(@NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            sh(result, commands);
            return result;
        }

        public static void sh(List<String> output, @NonNull String... commands) {
            sh(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        }

        public static void sh(List<String> output, List<String> error, @NonNull String... commands) {
            syncWrapper(false, output, error, commands);
        }

        @NonNull
        public static ArrayList<String> su(@NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            su(result, commands);
            return result;
        }

        public static void su(List<String> output, @NonNull String... commands) {
            su(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, commands);
        }

        public static void su(List<String> output, List<String> error, @NonNull String... commands) {
            syncWrapper(true, output, error, commands);
        }

        @NonNull
        public static ArrayList<String> loadScript(@NonNull InputStream in) {
            ArrayList<String> result = new ArrayList<>();
            loadScript(result, in);
            return result;
        }

        public static void loadScript(List<String> output, @NonNull InputStream in) {
            loadScript(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, in);
        }

        public static void loadScript(List<String> output, List<String> error, @NonNull InputStream in) {
            Shell.getShell().loadInputStream(output, error, in);
        }
    }

    @Deprecated
    public final static class Async {

        private Async() {}

        public interface Callback {
            void onTaskResult(@Nullable List<String> out, @Nullable List<String> err);
            void onTaskError(@NonNull Throwable err);
        }

        public static void sh(@NonNull String... commands) {
            sh(null, null, null, commands);
        }

        public static void sh(Shell.Async.Callback callback, @NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            sh(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, commands);
        }

        public static void sh(List<String> output, @NonNull String... commands) {
            sh(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, null, commands);
        }

        public static void sh(List<String> output, List<String> error, @NonNull String... commands) {
            sh(output, error, null, commands);
        }

        public static void sh(List<String> output, List<String> error,
                              Shell.Async.Callback callback, @NonNull String... commands) {
            asyncWrapper(false, output, error, callback, commands);
        }

        public static void su(@NonNull String... commands) {
            su(null, null, null, commands);
        }

        public static void su(Shell.Async.Callback callback, @NonNull String... commands) {
            ArrayList<String> result = new ArrayList<>();
            su(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, commands);
        }

        public static void su(List<String> output, @NonNull String... commands) {
            su(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, null, commands);
        }

        public static void su(List<String> output, List<String> error, @NonNull String... commands) {
            su(output, error, null, commands);
        }

        public static void su(List<String> output, List<String> error,
                              Shell.Async.Callback callback, @NonNull String... commands) {
            asyncWrapper(true, output, error, callback, commands);
        }

        public static void loadScript(InputStream in) {
            loadScript(null, null, null, in);
        }

        public static void loadScript(Shell.Async.Callback callback, @NonNull InputStream in) {
            ArrayList<String> result = new ArrayList<>();
            loadScript(result, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? result : null, callback, in);
        }

        public static void loadScript(List<String> output, @NonNull InputStream in) {
            loadScript(output, InternalUtils.hasFlag(FLAG_REDIRECT_STDERR) ? output : null, in);
        }

        public static void loadScript(List<String> output, List<String> error,
                                      @NonNull InputStream in) {
            loadScript(output, error, null, in);
        }

        public static void loadScript(List<String> output, List<String> error,
                                      Shell.Async.Callback callback, @NonNull InputStream in) {
            Shell.getShell(shell -> shell.loadInputStream(output, error, callback, in));
        }
    }

    protected static class InitializerCompat {
        @Deprecated
        public void onShellInit(@NonNull Shell shell) {}

        @Deprecated
        public boolean onShellInit(Context context, @NonNull Shell shell) throws Exception {
            // Backwards compatibility
            onShellInit(shell);
            return true;
        }

        @Deprecated
        public void onRootShellInit(@NonNull Shell shell) {}

        @Deprecated
        public boolean onRootShellInit(Context context, @NonNull Shell shell) throws Exception {
            // Backwards compatibility
            onRootShellInit(shell);
            return true;
        }

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
    }

    private static void syncWrapper(boolean root, List<String> output,
                                    List<String> error, String... commands) {
        Shell shell = Shell.getShell();
        if (root && shell.getStatus() == NON_ROOT_SHELL)
            return;
        shell.run(output, error, commands);
    }

    private static void asyncWrapper(boolean root, List<String> output,
                                     List<String> error, Shell.Async.Callback callback,
                                     String... commands) {
        Shell.getShell(shell -> {
            if (root && shell.getStatus() == NON_ROOT_SHELL)
                return;
            shell.run(output, error, callback, commands);
        });
    }

    abstract static class Impl extends Shell {
        @Override
        public Throwable run(List<String> outList, List<String> errList, @NonNull String... commands) {
            newJob().add(commands).to(outList, errList).exec();
            return null;
        }

        @Override
        public void run(List<String> outList, List<String> errList, Async.Callback callback, @NonNull String... commands) {
            newJob().add(commands).to(outList, errList)
                    .submit(callback == null ? null :
                            res -> callback.onTaskResult(res.getOut(), res.getErr()));
        }

        @Override
        public Throwable loadInputStream(List<String> outList, List<String> errList, @NonNull InputStream in) {
            newJob().add(in).to(outList, errList).exec();
            return null;
        }

        @Override
        public void loadInputStream(List<String> outList, List<String> errList, Async.Callback callback, @NonNull InputStream in) {
            newJob().add(in).to(outList, errList)
                    .submit(callback == null ? null :
                            res -> callback.onTaskResult(res.getOut(), res.getErr()));
        }
    }
}
