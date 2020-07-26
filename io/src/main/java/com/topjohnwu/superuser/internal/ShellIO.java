/*
 * Copyright 2020 John "topjohnwu" Wu
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

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuRandomAccessFile;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Locale;

class ShellIO extends SuRandomAccessFile implements DataInputImpl, DataOutputImpl {

    private static final String TAG = "SHELLIO";
    private static final byte[] JUNK = new byte[1];
    private static final FileNotFoundException FNF =
            new FileNotFoundException("No such file or directory");
    private static final UnsupportedOperationException UOE =
            new UnsupportedOperationException("Unsupported operation in shell backed I/O");

    private final SuFile file;
    private boolean readOnly;

    boolean eof;
    long fileOff;
    String WRITE_CONV;

    ShellIO(SuFile file, String mode) throws FileNotFoundException {
        this.file = file;
        if (file.isDirectory())
            throw FNF;
        fileOff = 0L;
        WRITE_CONV = "conv=notrunc";
        switch (mode) {
            case "r":
                if (!file.exists())
                    throw FNF;
                readOnly = true;
                break;
            case "w":
                if (!file.clear())
                    throw FNF;
                break;
            case "rw":
                if (!file.exists() && !file.createNewFile())
                    throw FNF;
                break;
        }
    }

    static ShellIO get(SuFile file, String mode) throws FileNotFoundException {
        if (file.isBlock())
            return new ShellBlockIO(file, mode);
        return new ShellIO(file, mode);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (readOnly)
            throw new IOException("File is opened as read-only");
        if (fileOff > 0 && fileOff < 512 && len > 512) {
            /* If fileOff is small, out block size will also be small and
            * causes extremely low I/O throughput. First write to 512, then
            * use at least 512B block size for writing */
            int size = 512 - (int) fileOff;
            write0(b, off, size);
            len -= size;
            off += size;
        }
        write0(b, off, len);
    }

    private void write0(@NonNull byte[] b, int off, int len) throws IOException {
        Shell.getShell().execTask((in, out, err) -> {
            String cmd;
            if (fileOff == 0) {
                cmd = String.format(Locale.ROOT,
                        "dd of='%s' bs=%d count=1 %s 2>/dev/null; echo",
                        file.getAbsolutePath(), len, WRITE_CONV);
            } else {
                cmd = String.format(Locale.ROOT,
                        "dd of='%s' ibs=%d count=1 obs=%d seek=1 %s 2>/dev/null; echo",
                        file.getAbsolutePath(), len, fileOff, WRITE_CONV);
            }
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            in.write(b, off, len);
            in.flush();
            // Wait till the operation is done
            out.read(JUNK);
        });
        fileOff += len;
    }

    /**
     * Optimized for stream based I/O
     */
    void streamWrite(byte[] b, int off, int len) throws IOException {
        Shell.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd bs=%d count=1 >> '%s' 2>/dev/null; echo", len, file.getAbsolutePath());
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            in.write(b, off, len);
            in.flush();
            // Wait till the operation is done
            out.read(JUNK);
        });
        fileOff += len;
    }

    @Override
    public void readFully(@NonNull byte[] b, int off, int len) throws IOException {
        if (read(b, off, len) != len)
            throw new EOFException();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;
        if (eof)
            return -1;
        // Try to use as large block as possible
        long gcd = ShellUtils.gcd(fileOff, len);
        if (gcd >= 512) {
            // Aligned, directly process it
            len = read(b, off, len, fileOff, gcd);
        } else {
            /* Unaligned reading is too slow, try reading with 512-byte aligned
            * and copy those in interest (still faster than unaligned reading) */
            long start = fileOff / 512 * 512;
            long end = (fileOff + len + 511) / 512 * 512;
            byte[] buf = new byte[(int) (end - start)];
            len = Math.min(read(buf, 0, buf.length, start, 512), len);
            if (len > 0)
                System.arraycopy(buf, (int) (fileOff - start), b, off, len);
        }
        if (len > 0)
            fileOff += len;
        return len;
    }

    /**
     * Optimized for stream based I/O
     */
    int streamRead(byte[] b) throws IOException {
        /* assert fileOff % b.length == 0 */
        int len = read(b, 0, b.length, fileOff, b.length);
        fileOff += len;
        return len;
    }

    private class Int {
        int i;
    }

    int read(byte[] b, int _off, int _len, long fileOff, long bs) throws IOException {
        /* assert fileOff % bs == 0 && _len % bs == 0 */
        if (eof)
            return -1;
        Int count = new Int();
        Shell.getShell().execTask((in, out, err) -> {
            int off = _off;
            int len = _len;
            String cmd = String.format(Locale.ROOT,
                    "dd if='%s' ibs=%d skip=%d count=%d obs=%d 2>/dev/null; echo >&2",
                    file.getAbsolutePath(), bs, fileOff / bs, len / bs, len);
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();

            // Poll until we read everything or operation is done
            while ((count.i != _len && err.available() == 0) || out.available() != 0) {
                int read = out.read(b, off, out.available());
                off += read;
                len -= read;
                count.i += read;
            }
            // Wait till the operation is done
            err.read(JUNK);
        });
        if (count.i != _len)
            eof = true;
        return count.i;
    }

    @Override
    public void seek(long pos) throws IOException {
        fileOff = pos;
    }

    @Override
    public void setLength(long newLength) throws IOException {
        if (newLength == 0) {
            if (!file.clear())
                throw new IOException("Cannot clear file");
            return;
        }
        Shell.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd of='%s' bs=%d seek=1 count=0 2>/dev/null; echo",
                    file.getAbsolutePath(), newLength);
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes("UTF-8"));
            in.write('\n');
            in.flush();
            // Wait till the operation is done
            out.read(JUNK);
        });
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public long getFilePointer() {
        return fileOff;
    }

    @Override
    public int skipBytes(int n) {
        long skip = Math.min(length(), fileOff + n) - fileOff;
        fileOff += skip;
        return (int) skip;
    }

    @Override
    public void close() { /* We don't actually hold resources */ }

    @Override
    public FileDescriptor getFD() {
        throw UOE;
    }

    @Override
    public FileChannel getChannel() {
        throw UOE;
    }
}
