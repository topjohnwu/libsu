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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

class ShellInputStream extends InputStream {

    private ShellFileIO io;
    private byte[] buf;
    private int count;
    private int bufOff;

    ShellInputStream(ShellFile file) throws FileNotFoundException {
        io = new ShellFileIO(file, "r");
        buf = new byte[4 * 1024 * 1024];
        count = 0;
        bufOff = 0;
    }

    private boolean fillBuffer() throws IOException {
        bufOff = 0;
        count = io.read(buf);
        return count > 0;
    }

    @Override
    public int read() throws IOException {
        if (count < 0)
            return -1;
        if (bufOff >= count && !fillBuffer())
            return -1;
        return buf[bufOff++] & 0xFF;
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;
        if (count < 0)
            return -1;
        int n = 0;
        while (n < len) {
            if (bufOff >= count && !fillBuffer())
                return n == 0 ? -1 : n;
            int size = Math.min(count - bufOff, len - n);
            System.arraycopy(buf, bufOff, b, off + n, size);
            bufOff += size;
            n += size;
        }
        return n;
    }
}
