/*
 * Copyright 2019 John "topjohnwu" Wu
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
import android.os.Build;
import android.text.TextUtils;

import com.topjohnwu.superuser.busybox.R;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * An initializer that installs and setup the bundled BusyBox.
 * <p>
 * {@code libsu} bundles with busybox binaries, supporting arm/arm64/x86/x64.
 * Register this class with {@link Shell.Config#addInitializers(Class[])} to let {@code libsu}
 * install and setup the bundled busybox binary to the app's internal storage.
 * The path containing all busybox applets will be <b>prepended</b> to {@code PATH}.
 * This makes sure all commands are using the applets from busybox, providing predictable
 * behavior so that developers can have less headache handling different implementation of the
 * common shell utilities. Some operations in {@link com.topjohnwu.superuser.io} depends on a
 * busybox to work properly, check before using them.
 */
public class BusyBoxInstaller extends Shell.Initializer {

    @Override
    public boolean onInit(Context context, @NonNull Shell shell) {
        Context de = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? context.createDeviceProtectedStorageContext() : context;

        File lib = new File(de.getApplicationInfo().nativeLibraryDir, "libbusybox.so");
        File bbPath = new File(de.getFilesDir().getParentFile(), "busybox");
        File bb = new File(bbPath, "busybox");

        bbPath.mkdir();
        shell.newJob().add(
                "rm -f " + bbPath + "/*",
                "ln -sf " + lib + " " + bb,
                bb + " --install -s " + bbPath,
                "export PATH=" + bbPath + ":$PATH"
        ).exec();

        return true;
    }
}
