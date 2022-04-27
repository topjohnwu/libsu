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

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.os.ParcelFileDescriptor.MODE_WRITE_ONLY;
import static com.topjohnwu.libsuexample.MainActivity.TAG;

import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IOFactory;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.nio.ExtendedFile;
import com.topjohnwu.superuser.nio.FileSystemManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;

public class StressTest {

    private static final Random r = new Random();

    private static FileSystemManager fs;
    private static FileCallback callback;

    interface FileCallback {
        void onFile(ExtendedFile file) throws Exception;
    }

    public static void perform(FileSystemManager s) {
        fs = s;
        Shell.EXECUTOR.execute(() -> {
            try {
                testShellIO();
                testRemoteIO();
            } catch (Exception e){
                Log.d(TAG, "", e);
            } finally {
                cancel();
            }
        });
    }

    public static void cancel() {
        // These shall force tons of exceptions and cancel the thread :)
        callback = null;
        fs = null;
    }

    private static void testShellIO() throws Exception {
        SuFile root = new SuFile("/system/app");

        // Stress test fifo IOStreams
        OutputStream out = IOFactory.fifoOut(new SuFile("/dev/null"), false);
        byte[] buf = new byte[64 * 1024];
        callback = file -> {
            try (InputStream in = IOFactory.fifoIn((SuFile) file)) {
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

    private static void testRemoteIO() throws Exception {
        ExtendedFile root = fs.newFile("/system/app");

        FileChannel out = fs.openChannel("/dev/null", MODE_WRITE_ONLY);
        ByteBuffer buf = ByteBuffer.allocateDirect(512 * 1024);
        callback = file -> {
            Log.d(TAG, file.getPath());
            try (FileChannel src = fs.openChannel(file, MODE_READ_ONLY)) {
                for (;;) {
                    // Randomize read/write length
                    int len = r.nextInt(buf.capacity());
                    buf.limit(len);
                    if (src.read(buf) <= 0)
                        break;
                    buf.flip();
                    out.write(buf);
                    buf.clear();
                }
            }
        };
        try {
            traverse(root);
        } finally {
            out.close();
        }
    }

    private static void traverse(ExtendedFile base) throws Exception {
        if (base.isDirectory()) {
            ExtendedFile[] ls = base.listFiles();
            if (ls == null)
                return;
            for (ExtendedFile file : ls) {
                traverse(file);
            }
        } else {
            callback.onFile(base);
        }
    }

}
