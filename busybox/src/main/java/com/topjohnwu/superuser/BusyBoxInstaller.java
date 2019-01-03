package com.topjohnwu.superuser;

import android.content.Context;
import android.os.Build;

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
 * Note: the busybox binaries will add around 1.42MB to your APK.
 */
public class BusyBoxInstaller extends Shell.Initializer {

    private static final String ARM_MD5 = "482cb744785ad638cf68931164853c71";
    private static final String X86_MD5 = "db75965c82d90b45777cbd6d114f6b47";
    private static final int APPLET_NUM = 339;

    @Override
    public boolean onInit(Context context, @NonNull Shell shell) {
        Context de = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? context.createDeviceProtectedStorageContext() : context;
        File bbPath = new File(de.getFilesDir().getParentFile(), "busybox");
        bbPath.mkdirs();
        File bb = new File(bbPath, "busybox");
        // Get architecture
        List<String> archs = Arrays.asList(Build.CPU_ABI);
        boolean x86 = archs.contains("x86");
        if (!bb.exists() || !ShellUtils.checkSum("MD5", bb, x86 ? X86_MD5 : ARM_MD5)) {
            for (File f : bbPath.listFiles()) {
                f.delete();
            }
            try (InputStream in = de.getResources().openRawResource(x86 ? R.raw.busybox_x86 : R.raw.busybox_arm);
                 OutputStream out = new FileOutputStream(bb)) {
                ShellUtils.pump(in, out);
            } catch (IOException e) {
                InternalUtils.stackTrace(e);
            }
            bb.setExecutable(true);
        }
        if (bbPath.listFiles().length != APPLET_NUM + 1) {
            shell.newJob().add(bbPath + "/busybox --install -s " + bbPath).exec();
        }
        shell.newJob().add("export PATH=" + bbPath + ":$PATH").exec();
        return true;
    }
}
