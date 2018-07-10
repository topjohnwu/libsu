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

package com.topjohnwu.superuser.internal;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuRandomAccessFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

class ShellFileIO extends SuRandomAccessFile implements DataInputImpl, DataOutputImpl {

    private static final String TAG = "SHELLIO";

    private String path;
    private long fileOff;
    private long fileSize;

    ShellFileIO(ShellFile file, String mode) throws FileNotFoundException {
        path = file.getPath();
        fileOff = 0L;
        if (TextUtils.equals(mode, "r")) {
            // Read
            if (!file.exists())
                throw new FileNotFoundException("No such file or directory");
            fileSize = file.length();
        } else if (TextUtils.equals(mode, "w")) {
            // Write
            if (!file.clear())
                throw new FileNotFoundException("No such file or directory");
            fileSize = 0L;
        } else if (TextUtils.equals(mode, "rw")) {
            // Random rw
            if (!file.exists() && !file.createNewFile())
                throw new FileNotFoundException("No such file or directory");
            fileSize = file.length();
        } else {
            throw new IllegalArgumentException("Illegal mode: " + mode);
        }
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        Throwable t = Shell.getShell().execTask((in, out, err) -> {
            // Writing without truncate is only possible with busybox
            String cmd = String.format(Locale.ROOT,
                    "busybox dd of='%s' obs=%d seek=%d ibs=%d count=1 conv=notrunc 2>/dev/null; echo done",
                    path, fileOff == 0 ? len : fileOff, fileOff == 0 ? 0 : 1, len);
            InternalUtils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            in.write(b, off, len);
            in.flush();
            // Wait till the operation is done
            ShellUtils.readFully(out, new byte[5]);
        });
        if (t != null)
            throw new IOException(t);
        fileOff += len;
        fileSize = Math.max(fileSize, fileOff);
    }

    /**
     * A write implementation that doesn't require busybox if only appending is required.
     * Very useful when implementing OutputStreams.
     */
    void append(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        Throwable t = Shell.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd bs=%d count=1 >> '%s' 2>/dev/null; echo done", len, path);
            InternalUtils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            in.write(b, off, len);
            in.flush();
            // Wait till the operation is done
            ShellUtils.readFully(out, new byte[5]);
        });
        if (t != null)
            throw new IOException(t);
        fileSize += len;
        fileOff = fileSize;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;
        // We cannot read over EOF
        int actualLen = (int) Math.min(len, fileSize - fileOff);
        if (actualLen <= 0)
            return -1;
        // Check alignment
        long gcd = ShellUtils.gcd(fileOff, actualLen);
        if (gcd >= 512) {
            // Aligned, directly process it
            readBlocks(b, off, actualLen, fileOff, gcd);
        } else {
            // Unaligned reading is too slow, read full 512-byte blocks and copy those in interest
            long start = fileOff / 512 * 512;
            long end = Math.min((fileOff + len + 511) / 512 * 512, fileSize);
            int startOff = (int) (fileOff - start);
            int readLen = (int) (end - start);
            byte[] buf = new byte[readLen];
            readBlocks(buf, 0, readLen, start, 512);
            System.arraycopy(buf, startOff, b, off, actualLen);
        }
        fileOff += actualLen;
        return actualLen;
    }

    private void readBlocks(byte[] b, int off, int len, long fileOff, long bs) throws IOException {
        /* assert fileOff % bs == 0 */
        Throwable t = Shell.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd if='%s' ibs=%d skip=%d count=%d obs=%d 2>/dev/null",
                    path, bs, fileOff / bs, (len + bs - 1) / bs, len);
            InternalUtils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            ShellUtils.readFully(out, b, off, len);
        });
        if (t != null)
            throw new IOException(t);
    }

    @Override
    public void seek(long pos) {
        fileOff = pos;
    }

    @Override
    public void setLength(long newLength) throws IOException {
        Throwable t = Shell.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd if=/dev/null of='%s' bs=%d seek=%d 2>/dev/null; echo done",
                    path, newLength == 0 ? 1 : newLength, newLength == 0 ? 0 : 1);
            InternalUtils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            // Wait till the operation is done
            ShellUtils.readFully(out, new byte[5]);
        });
        if (t != null)
            throw new IOException(t);
        fileSize = newLength;
    }

    @Override
    public long length() {
        return fileSize;
    }

    @Override
    public long getFilePointer() {
        return fileOff;
    }

    @Override
    public int skipBytes(int n) {
        long skip = Math.min(fileSize, fileOff + n) - fileOff;
        fileOff += skip;
        return (int) skip;
    }

    @Override
    public void close() { /* We don't actually hold resources */ }
}
