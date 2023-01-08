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

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

interface DataOutputImpl extends DataOutput {

    @Override
    default void write(int b) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = (byte) (b & 0xFF);
        write(buf);
    }

    @Override
    default void write(@NonNull byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    default void writeBoolean(boolean v) throws IOException {
        write(v ? 1 : 0);
    }

    @Override
    default void writeByte(int v) throws IOException {
        write(v);
    }

    @Override
    default void writeShort(int v) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte)(v >>> 8);
        b[1] = (byte)(v >>> 0);
        write(b);
    }

    @Override
    default void writeChar(int v) throws IOException {
        writeShort(v);
    }

    @Override
    default void writeInt(int v) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte)(v >>> 24);
        b[1] = (byte)(v >>> 16);
        b[2] = (byte)(v >>>  8);
        b[3] = (byte)(v >>>  0);
        write(b);
    }

    @Override
    default void writeLong(long v) throws IOException {
        byte[] b = new byte[8];
        b[0] = (byte)(v >>> 56);
        b[1] = (byte)(v >>> 48);
        b[2] = (byte)(v >>> 40);
        b[3] = (byte)(v >>> 32);
        b[4] = (byte)(v >>> 24);
        b[5] = (byte)(v >>> 16);
        b[6] = (byte)(v >>>  8);
        b[7] = (byte)(v >>>  0);
        write(b);
    }

    @Override
    default void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    @Override
    default void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    default void writeBytes(@NonNull String s) throws IOException {
        ByteOutputStream buf = new ByteOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeBytes(s);
        buf.writeTo(this);
    }

    @Override
    default void writeChars(@NonNull String s) throws IOException {
        ByteOutputStream buf = new ByteOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeChars(s);
        buf.writeTo(this);
    }

    @Override
    default void writeUTF(@NonNull String s) throws IOException {
        ByteOutputStream buf = new ByteOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.writeUTF(s);
        buf.writeTo(this);
    }
}
