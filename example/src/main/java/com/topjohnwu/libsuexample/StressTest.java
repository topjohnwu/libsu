/*
 * Copyright 2021 John "topjohnwu" Wu
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

package com.topjohnwu.libsuexample;

import android.os.Build;
import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IOFactory;
import com.topjohnwu.superuser.io.SuFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;

import static com.topjohnwu.libsuexample.MainActivity.TAG;

public class StressTest {

    private static List<String> console;
    private static OutputStream out;
    private static FileCallback callback;
    private static final byte[] buf = new byte[64 * 1024];

    interface FileCallback {
        void onFile(SuFile file) throws Exception;
    }

    public static void perform(List<String> c) {
        console = c;
        Shell.EXECUTOR.execute(() -> {
            try {
                run();
            } catch (Exception e){
                Log.d(TAG, "", e);
            } finally {
                cancel();
            }
        });
    }

    public static void cancel() {
        // These shall force tons of exceptions and cancel the thread :)
        console = null;
        callback = null;
        if (out != null) {
            try { out.close(); } catch (IOException ignored) {}
            out = null;
        }
    }

    private static void run() throws Exception {
        // Change to a more reasonable path if you don't want the
        // stress test to run forever
        SuFile root = new SuFile("/system");

        // This will stress test excessive root commands as SuFile runs
        // a bunch of them in a short period of time
        callback = file -> {
            file.isCharacter();
            file.isBlock();
            file.isSymlink();
        };
        traverse(root);

        if (Build.VERSION.SDK_INT >= 21) {
            // Stress test FifoOutputStream
            out = IOFactory.fifoOut(new SuFile("/dev/null"), false);

            // Make sure FifoInputStream works fine
            callback = file -> {
                try (InputStream in = IOFactory.fifoIn(file)) {
                    pump(in);
                }
            };
            traverse(root);
        } else {
            // Unfortunately, ShellOutputStream will crash BusyBox ASH :(
            // This means pre API 21, we cannot properly test root
            // outputs as CopyOutputStream does not actually pump data
            // directly to the target file.

            // In my personal environments, removing the BusyBoxInstaller
            // from shell initializers allows ShellOutputStream to operate
            // without issues, however due to these reasons, shell I/O
            // is strongly advised against.

            // /dev/null is writable without root
            out = new FileOutputStream("/dev/null");
        }

        // Make sure CopyInputStream works fine
        callback = file -> {
            try (InputStream in = IOFactory.copyIn(file)) {
                pump(in);
            }
        };
        traverse(root);

        // Make sure ShellInputStream works fine
        callback = file -> {
            try (InputStream in = IOFactory.shellIn(file)) {
                pump(in);
            }
        };
        traverse(root);
    }

    private static void traverse(SuFile base) throws Exception {
        console.add(base.getPath());
        if (base.isDirectory()) {
            SuFile[] ls = base.listFiles();
            if (ls == null)
                return;
            for (SuFile file : ls) {
                traverse(file);
            }
        } else {
            callback.onFile(base);
        }
    }

    public static void pump(InputStream in) throws IOException {
        Random r = new Random();
        for (;;) {
            // Randomize read/write length to test unaligned I/O
            int len = r.nextInt(buf.length);
            int read = in.read(buf, 0, len);
            if (read <= 0)
                break;
            out.write(buf, 0, read);
        }
        out.flush();
    }
}
