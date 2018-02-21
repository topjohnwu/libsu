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

package com.topjohnwu.superuser.io;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;

/**
 * An {@link java.io.InputStream} that read files by opening a new root process.
 * <p>
 * The difference between this class and {@link SuFileInputStream} is that this class does not
 * use a shell to do the I/O operations; instead it directly creates a new process to read the file.
 * In most cases, {@link SuFileInputStream} is sufficient; however if you expect a very high I/O
 * throughput (e.g. dumping large partitions), this class is created for this purpose.
 * <p>
 * Note: this class is <b>always buffered internally</b>, do not add another layer of
 * {@link java.io.BufferedInputStream} to add more overhead!
 */
public class SuProcessFileInputStream extends FilterInputStream {

    private Process process;

    /**
     * @see FileInputStream#FileInputStream(String)
     */
    public SuProcessFileInputStream(String path) throws FileNotFoundException {
        this(new File(path));
    }

    /**
     * @see FileInputStream#FileInputStream(File)
     */
    public SuProcessFileInputStream(File file) throws FileNotFoundException {
        super(null);
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    String.format("[ -e '%s' ] && echo 1 && cat '%s' || echo 0", file, file)});
            in = process.getInputStream();
            byte[] buf = new byte[2];
            ShellUtils.readFully(in, buf);
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
    public void close() throws IOException {
        in.close();
        process.destroy();
    }
}
