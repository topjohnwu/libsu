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

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuRandomAccessFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

class ShellFileIO extends SuRandomAccessFile implements DataInputImpl, DataOutputImpl {

    private static final String TAG = "SHELLIO";

    private SuFile file;
    private long fileOff;
    private long fileSize;

    ShellFileIO(SuFile file) throws FileNotFoundException {
        this.file = file;

        if (!file.exists()) {
            try {
                if (!file.createNewFile())
                    throw new FileNotFoundException();
            } catch (IOException e) {
                if (e instanceof FileNotFoundException)
                    throw (FileNotFoundException) e;
                throw (FileNotFoundException) new FileNotFoundException().initCause(e);
            }
        }

        fileOff = 0L;
        fileSize = file.length();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        ShellImpl shell = (ShellImpl) Shell.getShell();
        shell.lock.lock();
        try {
            // Only busybox dd is usable
            ShellUtils.cleanInputStream(shell.STDOUT);
            String cmd = String.format(Locale.ROOT,
                    "busybox dd of='%s' bs=1 seek=%d count=%d conv=notrunc 2>/dev/null; echo done",
                    file, fileOff, len);
            InternalUtils.log(TAG, cmd);
            shell.STDIN.write(cmd.getBytes("UTF-8"));
            shell.STDIN.write('\n');
            shell.STDIN.flush();
            shell.STDIN.write(b, off, len);
            shell.STDIN.flush();
            // Wait till the operation is done
            ShellUtils.readFully(shell.STDOUT, new byte[5]);
        } catch (IOException e) {
            shell.close();
            throw e;
        } finally {
            shell.lock.unlock();
        }
        fileOff += len;
        fileSize = Math.max(fileSize, fileOff);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;
        // We cannot read over EOF
        len = (int) Math.min(len, fileSize - fileOff);
        if (len <= 0)
            return -1;
        ShellImpl shell = (ShellImpl) Shell.getShell();
        shell.lock.lock();
        try {
            ShellUtils.cleanInputStream(shell.STDOUT);
            String cmd = String.format(Locale.ROOT,
                    "dd if='%s' bs=1 skip=%d count=%d 2>/dev/null",
                    file, fileOff, len);
            InternalUtils.log(TAG, cmd);
            shell.STDIN.write(cmd.getBytes("UTF-8"));
            shell.STDIN.write('\n');
            shell.STDIN.flush();
            ShellUtils.readFully(shell.STDOUT, b, off, len);
        } catch (IOException e) {
            shell.close();
            throw e;
        } finally {
            shell.lock.unlock();
        }
        fileOff += len;
        return len;
    }

    @Override
    public void seek(long pos) throws IOException {
        fileOff = pos;
    }

    @Override
    public void setLength(long newLength) throws IOException {
        ShellImpl shell = (ShellImpl) Shell.getShell();
        shell.lock.lock();
        try {
            String cmd = String.format(Locale.ROOT,
                    "dd if=/dev/null of='%s' bs=1 seek=%d 2>/dev/null; echo done",
                    file, newLength);
            InternalUtils.log(TAG, cmd);
            shell.STDIN.write(cmd.getBytes("UTF-8"));
            shell.STDIN.write('\n');
            shell.STDIN.flush();
            // Wait till the operation is done
            ShellUtils.readFully(shell.STDOUT, new byte[5]);
        } catch (IOException e) {
            shell.close();
            throw e;
        } finally {
            shell.lock.unlock();
        }
        fileSize = newLength;
    }

    @Override
    public long length() throws IOException {
        return fileSize;
    }

    @Override
    public long getFilePointer() throws IOException {
        return fileOff;
    }

    @Override
    public int skipBytes(int n) throws IOException {
        long skip = Math.min(fileSize, fileOff + n) - fileOff;
        fileOff += skip;
        return (int) skip;
    }

    @Override
    public void close() throws IOException { /* We don't actually hold resources */ }
}
