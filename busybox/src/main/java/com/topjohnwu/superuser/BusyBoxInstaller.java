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

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.internal.Utils;

import java.io.File;

/**
 * An initializer that installs and setup the bundled BusyBox.
 * <p>
 * {@code libsu} bundles with BusyBox binaries, supporting arm/arm64/x86/x64.
 * Register this class with {@link Shell.Builder#setInitializers(Class[])} to let {@code libsu}
 * install and setup the shell to use the bundled BusyBox binary.
 * <p>
 * After the initializer is run, the shell will be using BusyBox's "Standalone Mode ASH".
 * In this state, all commands will <b>always call the one in BusyBox regardless of PATH</b>.
 * To specifically call a command elsewhere, use the full path (e.g. {@code /system/bin/ls -l}).
 * This makes sure all commands are using the applets from BusyBox, providing predictable
 * behavior so that developers can have less headache handling different implementation of the
 * common shell utilities. Some operations in {@link com.topjohnwu.superuser.io} depends on
 * BusyBox to work properly, check before using them.
 */
public class BusyBoxInstaller extends Shell.Initializer {

    @Override
    public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
        Context de = Utils.getDeContext(context);

        File lib = new File(de.getApplicationInfo().nativeLibraryDir, "libbusybox.so");
        File bbPath = new File(de.getFilesDir().getParentFile(), "busybox");
        File bb = new File(bbPath, "busybox");

        bbPath.mkdir();
        shell.newJob().add(
                "rm -f " + bbPath + "/*",
                "ln -s " + lib + " " + bb,
                "export ASH_STANDALONE=1",
                "exec " + bb + " sh"
        ).exec();

        return true;
    }
}
