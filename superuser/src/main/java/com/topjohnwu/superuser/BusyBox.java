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
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * A class that handles the bundled BusyBox.
 * <p>
 * {@code libsu} bundles busybox binaries with arm/arm64 and x86/x64 covered.
 * Developers using {@code libsu} can setup busybox by calling {@link #setup(Context)}
 * before any new shell is created (e.g. the place where you call {@link Shell#setFlags(int)}), or
 * in the callback methods in {@link Shell.Initializer}.
 * After calling {@link #setup(Context)}, busybox will be installed in the app's internal storage,
 * and all new shells created will have the path to busybox <b>prepended</b> to {@code PATH}.
 * This makes sure all commands are using the applets from busybox, providing predictable
 * behavior so that developers can have less headache handling different implementation of the
 * common shell utilities. Some operations in {@link com.topjohnwu.superuser.io} depends on a
 * busybox to work properly, check before using them.
 * <p>
 * Note: the busybox binaries will add 1.42MB to your APK. For developers not willing
 * to use the busybox binaries bundled in {@code libsu}, you can let proguard remove it:
 * remember to <b>NOT</b> call {@link #setup(Context)} anywhere in your code, and enable both
 * <b>minifyEnabled</b> and <b>shrinkResources</b> in your release builds. For more info, please
 * check <a href="https://developer.android.com/studio/build/shrink-code.html">the official documentation</a>.
 */
public final class BusyBox {

    private BusyBox() {}

    /**
     * The path pointing to the folder where busybox is installed.
     * <p>
     * If your app would like to rely on external busybox, you can directly assign the path to this field.
     * All new shell instances created will have this directory <b>prepended</b> to {@code PATH}.
     * <p>
     * For example: Magisk Manager relies on Magisk's internal busybox (located in
     * {@code /sbin/.core/busybox}). So instead of calling {@link #setup(Context)}, it can directly
     * assign the busybox path to this field to discard the bundled busybox binaries.
     * ({@code BusyBox.BB_PATH = new File("/sbin/.core/busybox")}).
     */
    public static File BB_PATH = null;

    private static final String ARM_MD5 = "482cb744785ad638cf68931164853c71";
    private static final String X86_MD5 = "db75965c82d90b45777cbd6d114f6b47";
    private static final int APPLET_NUM = 339;
    private static boolean isInternalBusyBox = false;

    /**
     * Setup a busybox environment using the bundled busybox binaries.
     * <p>
     * Busybox will be installed to the internal data of the application. On Android 7.0+, it
     * will install busybox to Device Protected Storage, so developers willing to support
     * <a href="https://developer.android.com/training/articles/direct-boot.html">Direct Boot</a>
     * can use it without an issue.
     * <p>
     * After calling this method, {@link #BB_PATH} will point to the folder where the busybox is
     * installed.
     * <p>
     * If you are willing to let proguard to remove the bundled busybox binary to reduce the APK
     * size, do <b>NOT</b> call this method.
     * @param context a {@link Context} of the current app.
     */
    public static void setup(Context context) {
        isInternalBusyBox = true;
        Context de = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? context.createDeviceProtectedStorageContext() : context;
        BB_PATH = new File(de.getFilesDir().getParentFile(), "busybox");
        BB_PATH.mkdirs();
        File bb = new File(BB_PATH, "busybox");
        // Get architecture
        List<String> archs = Arrays.asList(Build.CPU_ABI);
        boolean x86 = archs.contains("x86");
        if (!bb.exists() || !ShellUtils.checkSum("MD5", bb, x86 ? X86_MD5 : ARM_MD5)) {
            for (File f : BB_PATH.listFiles()) {
                f.delete();
            }
            try (InputStream in = de.getResources().openRawResource(x86 ? R.raw.busybox_x86 : R.raw.busybox_arm);
                 OutputStream out = new FileOutputStream(bb)) {
                ShellUtils.pump(in, out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bb.setExecutable(true);
        }
    }

    static boolean init(Shell shell) {
        if (BB_PATH != null) {
            if (isInternalBusyBox && BB_PATH.listFiles().length != APPLET_NUM + 1) {
                shell.newJob().add(BB_PATH + "/busybox --install -s " + BB_PATH).exec();
            }
            shell.newJob().add("export PATH=" + BB_PATH + ":$PATH").exec();
            return true;
        }
        return false;
    }
}
