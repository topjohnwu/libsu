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

import android.support.annotation.NonNull;

import com.topjohnwu.superuser.internal.Factory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

public class SuFileOutputStream extends FilterOutputStream {

    public SuFileOutputStream(String path) throws FileNotFoundException {
        this(path, false);
    }

    public SuFileOutputStream(String path, boolean append) throws FileNotFoundException {
        this(new File(path), append);
    }

    public SuFileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    public SuFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(null);
        SuFile f;
        if (file instanceof SuFile)
            f = (SuFile) file;
        else
            f = new SuFile(file);
        if (f.useShell()) {
            // Use shell file io
            out = new BufferedOutputStream(Factory.createShellOutputStream(f, append), 4 * 1024 * 1024);
        } else {
            // Normal file output
            out = new BufferedOutputStream(new FileOutputStream(f, append));
        }
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }
}
