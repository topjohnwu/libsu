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

public class BusyBox {

    public static File BB_PATH = null;

    private static final String ARM_MD5 = "9adae2e0993fb6f233cd750f13393695";
    private static final String X86_MD5 = "498d1ec0b47e31b58074e533e664bd13";

    public static void setup(Context context) {
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
            try {
                Process p = Runtime.getRuntime().exec(bb + " --install -s " + BB_PATH);
                p.waitFor();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
