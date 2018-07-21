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

import android.support.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR;
import static com.topjohnwu.superuser.Shell.NON_ROOT_SHELL;

public class DeprecatedApiShim {
    public static class Sync {

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
            Shell.getShell().newJob(in).to(new Shell.Output(output, error)).exec();
        }
    }

    public static class Async {

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
            Shell.getShell(shell -> {
                Shell.Job job = shell.newJob(in).to(new Shell.Output(output, error));
                if (callback != null)
                    job.onResult(out -> callback.onTaskResult(out.getOut(), out.getErr()));
                job.enqueue();
            });
        }
    }

    private static void syncWrapper(boolean root, List<String> output,
                                    List<String> error, String... commands) {
        Shell shell = Shell.getShell();
        if (root && shell.getStatus() == NON_ROOT_SHELL)
            return;
        shell.newJob(commands).to(new Shell.Output(output, error)).exec();
    }

    private static void asyncWrapper(boolean root, List<String> output,
                                     List<String> error, Shell.Async.Callback callback,
                                     String... commands) {
        Shell.getShell(shell -> {
            if (root && shell.getStatus() == NON_ROOT_SHELL)
                return;
            Shell.Job job = shell.newJob(commands).to(new Shell.Output(output, error));
            if (callback != null)
                job.onResult(out -> callback.onTaskResult(out.getOut(), out.getErr()));
            job.enqueue();
        });
    }
}
