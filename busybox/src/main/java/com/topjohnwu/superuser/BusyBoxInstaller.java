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
 * {@code libsu} bundles with busybox binaries, supporting arm/arm64 and x86/x64.
 * Register this class with {@link Shell.Config#addInitializers(Class[])} to let {@code libsu}
 * install and setup the bundled busybox binary to the app's internal storage.
 * The path containing all busybox applets will be <b>prepended</b> to {@code PATH}.
 * This makes sure all commands are using the applets from busybox, providing predictable
 * behavior so that developers can have less headache handling different implementation of the
 * common shell utilities. Some operations in {@link com.topjohnwu.superuser.io} depends on a
 * busybox to work properly, check before using them.
 * <p>
 * Note: the busybox binaries will add around 1.51MB to your APK.
 */
public class BusyBoxInstaller extends Shell.Initializer {

    private static final String ARM_MD5 = "322eacc36d95d17dafba0d4cefe9c73c";
    private static final String X86_MD5 = "7101310d3826f14f90dd0e5e2ae5e52c";
    private static final int APPLET_NUM = 323;

    @Override
    public boolean onInit(Context context, @NonNull Shell shell) {
        Context de = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? context.createDeviceProtectedStorageContext() : context;
        File bbPath = new File(de.getFilesDir().getParentFile(), "busybox");
        File bb = new File(bbPath, "busybox");
        // Get architecture
        boolean x86;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<String> abis = Arrays.asList(Build.SUPPORTED_ABIS);
            x86 = abis.contains("x86");
        } else {
            x86 = TextUtils.equals(Build.CPU_ABI, "x86");
        }
        if (!bb.exists() || !ShellUtils.checkSum("MD5", bb, x86 ? X86_MD5 : ARM_MD5)) {
            bbPath.mkdirs();
            for (File f : bbPath.listFiles())
                f.delete();
            try (InputStream in = de.getResources().openRawResource(
                    x86 ? R.raw.busybox_x86 : R.raw.busybox_arm);
                 OutputStream out = new FileOutputStream(bb)) {
                ShellUtils.pump(in, out);
            } catch (IOException e) {
                InternalUtils.stackTrace(e);
                return true;
            }
        }
        if (bbPath.listFiles().length != APPLET_NUM + 1) {
            try {
                bb.setExecutable(true);
                Runtime.getRuntime().exec(new String[] {
                        bb.getPath(), "--install", "-s", bbPath.getPath() }).waitFor();
            } catch (InterruptedException | IOException e) {
                InternalUtils.stackTrace(e);
                return true;
            }
        }
        shell.newJob().add("export PATH=" + bbPath + ":$PATH").exec();
        return true;
    }
}
