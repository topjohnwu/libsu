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

import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;

public class SuProcessFileInputStream extends FilterInputStream {

    private Process process;

    public SuProcessFileInputStream(String path) throws FileNotFoundException {
        this(new File(path));
    }

    public SuProcessFileInputStream(File file) throws FileNotFoundException {
        super(null);
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c",
                    String.format("[ -e '%s' ] && echo 1 && cat '%s' || echo 0", file, file)});
            in = process.getInputStream();
            byte[] buf = new byte[2];
            InternalUtils.readFully(in, buf);
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
