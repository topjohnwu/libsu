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

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

interface DataInputImpl extends DataInput {

    int read(byte[] b, int off, int len) throws IOException;

    default int read() throws IOException {
        byte[] b = new byte[1];
        if (read(b) != 1)
            return -1;
        return b[0] & 0xFF;
    }

    default int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    default void readFully(@NonNull byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    default void readFully(byte[] b, int off, int len) throws IOException {
        if (read(b, off, len) != len)
            throw new EOFException();
    }

    @Override
    default boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    default byte readByte() throws IOException {
        byte[] b = new byte[1];
        readFully(b);
        return b[0];
    }

    @Override
    default int readUnsignedByte() throws IOException {
        return readByte() & 0xFF;
    }

    @Override
    default short readShort() throws IOException {
        byte[] b = new byte[2];
        readFully(b);
        return (short)((b[0] << 8) + (b[1] << 0));
    }

    @Override
    default int readUnsignedShort() throws IOException {
        byte[] b = new byte[2];
        readFully(b);
        return (b[0] << 8) + (b[1] << 0);
    }

    @Override
    default char readChar() throws IOException {
        return (char) readUnsignedShort();
    }

    @Override
    default int readInt() throws IOException {
        byte[] b = new byte[4];
        readFully(b);
        return ((b[0] << 24) + (b[1] << 16) + (b[2] << 8) + (b[3] << 0));
    }

    @Override
    default long readLong() throws IOException {
        byte[] b = new byte[8];
        readFully(b);
        return (((long)b[0] << 56) +
                ((long)(b[1] & 255) << 48) +
                ((long)(b[2] & 255) << 40) +
                ((long)(b[3] & 255) << 32) +
                ((long)(b[4] & 255) << 24) +
                ((b[5] & 255) << 16) +
                ((b[6] & 255) <<  8) +
                ((b[7] & 255) <<  0));
    }

    @Override
    default float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    default double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    default String readLine() throws IOException {
        ByteOutputStream buf = new ByteOutputStream();
        try {
            int b;
            do {
                b = readUnsignedByte();
                buf.write(b);
            } while (b != '\n');
        } catch (EOFException ignored) {}

        int size = buf.size();
        if (size == 0)
            return null;

        // Strip new line and carriage return
        byte[] bytes = buf.getBuf();
        if (bytes[size - 1] == '\n') {
            size -= 1;
            if (size > 0 && bytes[size - 1] == '\r')
                size -= 1;
        }

        return new String(bytes, 0, size, UTF_8);
    }

    @NonNull
    @Override
    default String readUTF() throws IOException {
        int len = readUnsignedShort();
        byte[] b = new byte[len + 2];
        b[0] = (byte)(len >>> 8);
        b[1] = (byte)(len >>> 0);
        readFully(b, 2, len);
        return new DataInputStream(new ByteArrayInputStream(b)).readUTF();
    }
}
