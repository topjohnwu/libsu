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

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.io.SuFile;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * An implementation specialized for block devices
 */
class ShellBlockIO extends ShellIO {

    private final long blockSize;

    ShellBlockIO(SuFile file, String mode) throws FileNotFoundException {
        super(file, mode);
        long bs;
        try {
            bs = Long.parseLong(ShellUtils.fastCmd(
                    "blockdev --getsize64 " + file.getEscapedPath()));
        } catch (NumberFormatException e) {
            // Error, no choice but to assume no boundary
            bs = Long.MAX_VALUE;
        }
        blockSize = bs;
    }

    @Override
    protected String getConv() {
        return "";
    }

    @Override
    protected int alignedRead(byte[] b, int _off, int count, int skip, int bs) throws IOException {
        // dd skip past boundary is extremely slow, avoid it
        if (skip * bs >= blockSize) {
            eof = true;
            return -1;
        }
        return super.alignedRead(b, _off, count, skip, bs);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        if (fileOff + len > blockSize)
            throw new IOException("Cannot write pass block size");
        super.write(b, off, len);
    }

    @Override
    public long length() {
        return blockSize;
    }

    @Override
    public void setLength(long newLength) {
        throw new UnsupportedOperationException("Block devices have fixed sizes");
    }

    @Override
    public void seek(long pos) throws IOException {
        if (pos > blockSize)
            throw new IOException("Cannot seek pass block size");
        fileOff = pos;
    }
}
