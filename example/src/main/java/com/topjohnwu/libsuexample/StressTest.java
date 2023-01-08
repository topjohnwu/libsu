/*
 * Copyright 2023 John "topjohnwu" Wu
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
import static com.topjohnwu.superuser.nio.FileSystemManager.MODE_READ_ONLY;
import static com.topjohnwu.superuser.nio.FileSystemManager.MODE_WRITE_ONLY;

import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuRandomAccessFile;
import com.topjohnwu.superuser.nio.ExtendedFile;
import com.topjohnwu.superuser.nio.FileSystemManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class StressTest {

    interface FileCallback {
        void onFile(ExtendedFile file) throws Exception;
    }

    private static final String TEST_DIR= "/system/app";
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final Random r = new Random();
    private static final MessageDigest md;

    static {
        MessageDigest m;
        try {
            m = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            m = null;
        }
        md = m;
    }

    private static FileSystemManager remoteFS;
    private static FileCallback callback;
    private static Map<String, byte[]> hashes;

    public static void perform(FileSystemManager fs) {
        remoteFS = fs;
        Shell.EXECUTOR.execute(() -> {
            try {
                collectHashes();
                // Test I/O streams
                testShellStream();
                testRemoteStream();
                // Test random I/O
                testShellRandomIO();
                testRemoteChannel();
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
        remoteFS = null;
        hashes = null;
    }

    private static void collectHashes() throws Exception {
        FileSystemManager fs = FileSystemManager.getLocal();
        ExtendedFile root = fs.getFile(TEST_DIR);

        // Collect checksums of all files in test dir and use it as a reference
        // to verify the correctness of the several I/O implementations.
        Map<String, byte[]> map = new HashMap<>();
        byte[] buf = new byte[BUFFER_SIZE];
        callback = file -> {
            try (InputStream in = file.newInputStream()) {
                for (;;) {
                    int read = in.read(buf);
                    if (read <= 0)
                        break;
                    md.update(buf, 0, read);
                }
            }
            map.put(file.getPath(), md.digest());
        };
        traverse(root);
        hashes = map;
    }

    private static void testShellStream() throws Exception {
        SuFile root = new SuFile(TEST_DIR);
        SuFile outFile = new SuFile("/dev/null");
        testIOStream(root, outFile);
    }

    private static void testRemoteStream() throws Exception {
        ExtendedFile root = remoteFS.getFile(TEST_DIR);
        ExtendedFile outFile = remoteFS.getFile("/dev/null");
        testIOStream(root, outFile);
    }

    private static void testIOStream(ExtendedFile root, ExtendedFile outFile) throws Exception {
        OutputStream out = outFile.newOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        callback = file -> {
            Log.d(TAG, file.getClass().getSimpleName() + " stream: " + file.getPath());
            try (InputStream in = file.newInputStream()) {
                for (;;) {
                    int read = in.read(buf);
                    if (read <= 0)
                        break;
                    out.write(buf, 0, read);
                    md.update(buf, 0, read);
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

    private static void testShellRandomIO() throws Exception {
        SuFile root = new SuFile(TEST_DIR);

        OutputStream out = new SuFile("/dev/null").newOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        callback = file -> {
            Log.d(TAG, "SuRandomAccessFile: " + file.getPath());
            try (SuRandomAccessFile in = SuRandomAccessFile.open(file, "r")) {
                for (;;) {
                    // Randomize read/write length to test unaligned I/O
                    int len = r.nextInt(buf.length);
                    int read = in.read(buf, 0, len);
                    if (read <= 0)
                        break;
                    out.write(buf, 0, read);
                    md.update(buf, 0, read);
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

    private static void testRemoteChannel() throws Exception {
        ExtendedFile root = remoteFS.getFile(TEST_DIR);

        FileChannel out = remoteFS.openChannel("/dev/null", MODE_WRITE_ONLY);
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        callback = file -> {
            Log.d(TAG, "RemoteFileChannel: " + file.getPath());
            try (FileChannel src = remoteFS.openChannel(file, MODE_READ_ONLY)) {
                for (;;) {
                    // Randomize read/write length
                    int len = r.nextInt(buf.capacity());
                    buf.limit(len);
                    if (src.read(buf) <= 0)
                        break;
                    buf.flip();
                    out.write(buf);
                    buf.rewind();
                    md.update(buf);
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

    private static void verifyHash(ExtendedFile file) {
        if (hashes == null)
            return;
        byte[] refHash = hashes.get(file.getPath());
        if (refHash == null) {
            Log.e(TAG, "ref hash is null: " + file.getPath());
        } else if (!Arrays.equals(refHash, md.digest())) {
            Log.e(TAG, file.getClass().getSimpleName() +
                    " hash mismatch: " + file.getPath());
        }
    }

    private static void traverse(ExtendedFile file) throws Exception {
        if (file.isSymlink())
            return;
        if (file.isDirectory()) {
            ExtendedFile[] ls = file.listFiles();
            if (ls == null)
                return;
            for (ExtendedFile child : ls) {
                traverse(child);
            }
        } else {
            md.reset();
            callback.onFile(file);
            verifyHash(file);
        }
    }

}
