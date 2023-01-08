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

import com.topjohnwu.superuser.io.SuRandomAccessFile;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

class RAFWrapper extends SuRandomAccessFile {

    private final RandomAccessFile raf;

    RAFWrapper(File file, String mode) throws FileNotFoundException {
        raf = new RandomAccessFile(file, mode);
    }

    @Override
    public int read() throws IOException {
        return raf.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return raf.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return raf.read(b);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return raf.skipBytes(n);
    }

    @Override
    public void write(int b) throws IOException {
        raf.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        raf.write(b);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        raf.write(b, off, len);
    }

    @Override
    public void seek(long pos) throws IOException {
        raf.seek(pos);
    }

    @Override
    public void setLength(long newLength) throws IOException {
        raf.setLength(newLength);
    }

    @Override
    public long length() throws IOException {
        return raf.length();
    }

    public long getFilePointer() throws IOException {
        return raf.getFilePointer();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        raf.writeBoolean(v);
    }

    @Override
    public void writeByte(int v) throws IOException {
        raf.writeByte(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        raf.writeShort(v);
    }

    @Override
    public void writeChar(int v) throws IOException {
        raf.writeChar(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        raf.writeInt(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        raf.writeLong(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        raf.writeFloat(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        raf.writeDouble(v);
    }

    @Override
    public void writeBytes(@NonNull String s) throws IOException {
        raf.writeBytes(s);
    }

    @Override
    public void writeChars(@NonNull String s) throws IOException {
        raf.writeChars(s);
    }

    @Override
    public void writeUTF(@NonNull String s) throws IOException {
        raf.writeUTF(s);
    }

    @Override
    public void readFully(@NonNull byte[] b) throws IOException {
        raf.readFully(b);
    }

    @Override
    public void readFully(@NonNull byte[] b, int off, int len) throws IOException {
        raf.readFully(b, off, len);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return raf.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return raf.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return raf.readUnsignedByte();
    }

    @Override
    public short readShort() throws IOException {
        return raf.readShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return raf.readUnsignedShort();
    }

    @Override
    public char readChar() throws IOException {
        return raf.readChar();
    }

    @Override
    public int readInt() throws IOException {
        return raf.readInt();
    }

    @Override
    public long readLong() throws IOException {
        return raf.readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return raf.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return raf.readDouble();
    }

    @Override
    public String readLine() throws IOException {
        return raf.readLine();
    }

    @Override
    public String readUTF() throws IOException {
        return raf.readUTF();
    }

    public FileDescriptor getFD() throws IOException {
        return raf.getFD();
    }

    public FileChannel getChannel() {
        return raf.getChannel();
    }
}
