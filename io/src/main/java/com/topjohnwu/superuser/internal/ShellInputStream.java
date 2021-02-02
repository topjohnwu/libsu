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

import com.topjohnwu.superuser.io.SuFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class makes sure actual I/O is always done in size of chunks (buf.length) regardless
 * whatever happens, including when user requests mark/reset operations.
 * The reason is because unaligned I/O using shell is extremely inefficient.
 */
class ShellInputStream extends InputStream {

    // Set chunk size as 4MB
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;

    private final ShellIO io;
    private final byte[] buf;

    // Number of valid bytes in buf
    private int count = 0;
    // Current read pos, > 0 in buf, < 0 in markBuf (interpret in bitwise negate)
    private int pos = 0;

    // -1 when no active mark, 0 when markBuf is active, pos when mark is called
    private int markPos = -1;
    // Number of valid bytes in markBuf
    private int markBufCount = 0;

    // markBuf.length == markBufSize
    private int markBufSize;
    private byte[] markBuf;

    // Some value ranges:
    // 0 <= count <= buf.length
    // 0 <= pos <= count (if pos > 0)
    // 0 <= markPos <= pos (markPos = -1 means no mark)
    // 0 <= ~pos <= markBufCount (if pos < 0)
    // 0 <= markBufCount <= markLimit

    ShellInputStream(SuFile file) throws FileNotFoundException {
        io = ShellIO.get(file, "r");
        buf = new byte[CHUNK_SIZE];
    }

    @Override
    public int read() throws IOException {
        byte[] BYTE = new byte[1];
        if (read(BYTE) != 1)
            return -1;
        return BYTE[0] & 0xFF;
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len > b.length)
            throw new IndexOutOfBoundsException();
        if (count < 0)
            return -1;
        return read0(len, b, off);
    }

    private synchronized int read0(int len, byte[] b, int off) throws IOException {
        int n = 0;
        while (n < len) {
            if (pos < 0) {
                // Read from markBuf
                int pos = ~this.pos;
                int size = Math.min(markBufCount - pos, len - n);
                if (b != null)
                    System.arraycopy(markBuf, pos, b, off + n, size);
                n += size;
                pos += size;
                if (pos == markBufCount) {
                    // markBuf done, switch to buf
                    this.pos = 0;
                } else {
                    // continue reading markBuf
                    this.pos = ~pos;
                }
                continue;
            }
            // Read from buf
            if (pos >= count) {
                // We ran out of buffer, need to either refill or abort
                if (markPos >= 0) {
                    // We need to preserve some buffer for mark
                    int size = count - markPos;
                    if ((markBufSize - markBufCount) < size) {
                        // Out of mark limit, discard markBuf
                        markBuf = null;
                        markBufCount = 0;
                        markPos = -1;
                    } else if (markBuf == null) {
                        markBuf = new byte[markBufSize];
                        markBufCount = 0;
                    }
                    if (markBuf != null) {
                        // Accumulate data in markBuf
                        System.arraycopy(buf, markPos, markBuf, markBufCount, size);
                        markBufCount += size;
                        // Set markPos to 0 as buffer will refill
                        markPos = 0;
                    }
                }
                // refill buffer
                pos = 0;
                count = io.streamRead(buf);
                if (count < 0)
                    return n == 0 ? -1 : n;
            }
            int size = Math.min(count - pos, len - n);
            if (b != null)
                System.arraycopy(buf, pos, b, off + n, size);
            n += size;
            pos += size;
        }
        return n;
    }

    @Override
    public synchronized void mark(int readlimit) {
        // Reset mark
        markPos = pos;
        markBufCount = 0;
        markBuf = null;

        int remain = count - pos;
        if (readlimit <= remain) {
            // Don't need a separate buffer
            markBufSize = 0;
        } else {
            // Extra buffer required is remain + n * buf.length
            markBufSize = remain + ((readlimit - remain) / buf.length) * buf.length;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPos < 0)
            throw new IOException("Resetting to invalid mark");
        // Switch to markPos or use markBuf
        pos = markBuf == null ? markPos : ~0;
    }

    @Override
    public synchronized int available() throws IOException {
        if (count < 0)
            return 0;
        if (pos >= count) {
            // Try to read the next chunk into memory
            read0(1, null, 0);
            if (count < 0)
                return 0;
            // Revert the 1 byte read
            --pos;
        }
        // Return the size available in memory
        if (pos < 0)
            return (markBufCount - ~pos) + count;
        else
            return count - pos;
    }

    @Override
    public long skip(long n) throws IOException {
        // Don't support backwards skip
        if (n < 0)
            return 0;
        return Math.max(read0((int) n, null, 0), 0);
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
