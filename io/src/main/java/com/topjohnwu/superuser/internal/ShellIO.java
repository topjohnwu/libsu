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

package com.topjohnwu.superuser.internal;

import static com.topjohnwu.superuser.internal.IOFactory.JUNK;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuRandomAccessFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

class ShellIO extends SuRandomAccessFile implements DataInputImpl, DataOutputImpl {

    private static final String TAG = "SHELLIO";

    private final SuFile file;
    private boolean readOnly;

    boolean eof;
    long fileOff;

    static ShellIO get(SuFile file, String mode) throws FileNotFoundException {
        if (file.isBlock())
            return new ShellBlockIO(file, mode);
        return new ShellIO(file, mode);
    }

    ShellIO(SuFile file, String mode) throws FileNotFoundException {
        FileNotFoundException fnf = new FileNotFoundException("No such file or directory");
        this.file = file;
        if (file.isDirectory())
            throw fnf;
        fileOff = 0L;
        switch (mode) {
            case "r":
                if (!file.exists())
                    throw fnf;
                readOnly = true;
                break;
            case "w":
                if (!file.clear())
                    throw fnf;
                break;
            case "rw":
                if (!file.exists() && !file.createNewFile())
                    throw fnf;
                break;
        }
    }

    protected String getConv() {
        return "conv=notrunc";
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (readOnly)
            throw new IOException("File is opened as read-only");
        if (fileOff > 0 && fileOff < 512 && len > 512) {
            // If fileOff is small, out block size will also be small and
            // causes extremely low I/O throughput. First write to 512, then
            // use at least 512B block size for writing
            int size = 512 - (int) fileOff;
            write0(b, off, size);
            len -= size;
            off += size;
        }
        write0(b, off, len);
    }

    private void write0(@NonNull byte[] b, int off, int len) throws IOException {
        file.getShell().execTask((in, out, err) -> {
            String cmd;
            if (fileOff == 0) {
                cmd = String.format(Locale.ROOT,
                        "dd of=%s bs=%d count=1 %s 2>/dev/null; echo\n",
                        file.getEscapedPath(), len, getConv());
            } else {
                cmd = String.format(Locale.ROOT,
                        "dd of=%s ibs=%d count=1 obs=%d seek=1 %s 2>/dev/null; echo\n",
                        file.getEscapedPath(), len, fileOff, getConv());
            }
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes(UTF_8));
            in.flush();
            in.write(b, off, len);
            in.flush();
            // Wait till the operation is done
            out.read(JUNK);
        });
        fileOff += len;
    }

    @Override
    public int read() throws IOException {
        return DataInputImpl.super.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return DataInputImpl.super.read(b);
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
        int bs = (int) ShellUtils.gcd(fileOff, len);
        if (bs >= 512 || len < 512) {
            // Aligned or small reads, directly process it
            len = alignedRead(b, off, len / bs, (int) (fileOff / bs), bs);
        } else {
            // Unaligned reading is too slow, try reading with 4K aligned
            // and copy those in interest (still faster than unaligned reading)
            bs = 4096;

            long skip = fileOff / bs;
            int count = (int) ((fileOff + len + bs - 1) / bs - skip);
            byte[] buf = new byte[count * bs];
            long start = skip * bs;
            int read = alignedRead(buf, 0, count, (int) skip, bs);
            if (read > 0) {
                int valid = (int) (start + read - fileOff);
                if (valid < len)
                    eof = true;
                len = Math.min(valid, len);
                System.arraycopy(buf, (int) (fileOff - start), b, off, len);
            }
        }
        fileOff += len;
        return len == 0 ? -1 : len;
    }

    // return actual bytes read, always >= 0
    protected int alignedRead(byte[] b, int _off, int count, int skip, int bs) throws IOException {
        // fail fast
        if (eof)
            return 0;
        int[] total = new int[1];
        int len = count * bs;
        file.getShell().execTask((in, out, err) -> {
            int off = _off;
            String cmd = String.format(Locale.ROOT,
                    "dd if=%s ibs=%d skip=%d count=%d obs=%d 2>/dev/null; echo >&2\n",
                    file.getEscapedPath(), bs, skip, count, len);
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes(UTF_8));
            in.flush();

            // Poll until we read everything
            while ((total[0] != len && err.available() == 0) || out.available() != 0) {
                int read = out.read(b, off, out.available());
                off += read;
                total[0] += read;
            }
            // Wait till the operation is done for synchronization
            err.read(JUNK);
        });
        if (total[0] == 0 || total[0] != len)
            eof = true;
        return total[0];
    }

    @Override
    public String readLine() throws IOException {
        ByteOutputStream bs = new ByteOutputStream();
        boolean eos = false;

        // Continually read aligned 512-byte blocks and check for new line
        do {
            long skip = fileOff / 512;
            byte[] buf = new byte[512];
            int read = alignedRead(buf, 0, 1, (int) skip, 512);
            if (read == 0)
                break;
            int i = (int) (fileOff - skip * 512);
            for (; i < read; ++i) {
                byte b = buf[i];
                bs.write(b);
                if (b == '\n') {
                    ++i;
                    eos = true;
                    break;
                }
            }
            if (eof) {
                // alignedRead hit eof, double check if we have reached it
                if (i != read)
                    eof = false;
            }
        } while (!eof && !eos);

        int size = bs.size();
        if (size == 0)
            return null;

        fileOff += size;

        // Strip new line and carriage return
        byte[] bytes = bs.getBuf();
        if (bytes[size - 1] == '\n') {
            size -= 1;
            if (size > 0 && bytes[size - 1] == '\r')
                size -= 1;
        }

        return new String(bytes, 0, size, UTF_8);
    }

    @Override
    public void seek(long pos) throws IOException {
        fileOff = pos;
        eof = false;
    }

    @Override
    public void setLength(long newLength) throws IOException {
        if (newLength == 0) {
            if (!file.clear())
                throw new IOException("Cannot clear file");
            return;
        }
        file.getShell().execTask((in, out, err) -> {
            String cmd = String.format(Locale.ROOT,
                    "dd of=%s bs=%d seek=1 count=0 2>/dev/null; echo\n",
                    file.getEscapedPath(), newLength);
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes(UTF_8));
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
        if (n <= 0)
            return 0;
        long old = fileOff;
        fileOff = Math.min(length(), fileOff + n);
        return (int) (fileOff - old);
    }

    @Override
    public void close() { /* We don't actually hold resources */ }

}
