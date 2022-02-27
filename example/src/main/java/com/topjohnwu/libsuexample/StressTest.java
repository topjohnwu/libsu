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

import static com.topjohnwu.libsuexample.MainActivity.TAG;

import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IOFactory;
import com.topjohnwu.superuser.io.SuFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;

public class StressTest {

    private static List<String> console;
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

        // Stress test fifo IOStreams
        OutputStream out = IOFactory.fifoOut(new SuFile("/dev/null"), false);
        Random r = new Random();
        callback = file -> {
            try (InputStream in = IOFactory.fifoIn(file)) {
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
        };
        try {
            traverse(root);
        } finally {
            out.close();
        }
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

}
