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

package com.topjohnwu.superuser.io;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link java.io.OutputStream} that write files by opening a new root process.
 * <p>
 * The difference between this class and {@link SuFileOutputStream} is that this class does not
 * use a shell to do the I/O operations; instead it directly creates a new process to write the file.
 * In most cases, {@link SuFileOutputStream} is sufficient; however if you expect a very high I/O
 * throughput (e.g. restoring large partitions), this class is created for this purpose.
 * <p>
 * Note: this class is <b>always buffered internally</b>, do not add another layer of
 * {@link java.io.BufferedOutputStream} to add more overhead!
 */
@Deprecated
public class SuProcessFileOutputStream extends FilterOutputStream {

    private Process process;

    /**
     * @see FileOutputStream#FileOutputStream(String)
     */
    public SuProcessFileOutputStream(String path) throws FileNotFoundException {
        this(path, false);
    }

    /**
     * @see FileOutputStream#FileOutputStream(String, boolean)
     */
    public SuProcessFileOutputStream(String path, boolean append) throws FileNotFoundException {
        this(new File(path), append);
    }

    /**
     * @see FileOutputStream#FileOutputStream(File)
     */
    public SuProcessFileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    /**
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    public SuProcessFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(null);
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    String.format("touch '%s' && echo 1 && cat - %s '%s' || echo 0",
                            file, append ? ">>" : ">", file)});
            out = process.getOutputStream();
            InputStream in = process.getInputStream();
            byte[] buf = new byte[2];
            DataInputStream dis = new DataInputStream(in);
            dis.readFully(buf);
            if (buf[0] == '0') {
                close();
                throw new FileNotFoundException("No such file or directory");
            }
        } catch (IOException e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            throw (FileNotFoundException)
                    new FileNotFoundException("Error starting process").initCause(e);
        }
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        out.close();
        process.destroy();
    }
}
