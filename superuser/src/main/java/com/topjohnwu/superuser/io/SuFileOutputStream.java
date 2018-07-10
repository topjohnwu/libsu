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

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.Factory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

/**
 * An {@link java.io.OutputStream} that read files using the global shell instance.
 * <p>
 * This class always checks whether using a shell is necessary. If not, it simply opens a new
 * {@link FileOutputStream}.
 * <p>
 * Note: this class is <b>always buffered internally</b>, do not add another layer of
 * {@link BufferedOutputStream} to add more overhead!
 */
public class SuFileOutputStream extends FilterOutputStream {

    /**
     * @see FileOutputStream#FileOutputStream(String)
     */
    public SuFileOutputStream(String path) throws FileNotFoundException {
        this(path, false);
    }

    /**
     * @see FileOutputStream#FileOutputStream(String, boolean)
     */
    public SuFileOutputStream(String path, boolean append) throws FileNotFoundException {
        this(new File(path), append);
    }

    /**
     * @see FileOutputStream#FileOutputStream(File)
     */
    public SuFileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    /**
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    public SuFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(null);
        if (file instanceof SuFile && ((SuFile) file).isSU()) {
            out = new BufferedOutputStream(
                    Factory.createShellOutputStream(((SuFile) file).getShellFile(), append),
                    4 * 1024 * 1024);
        } else {
            try {
                // Try normal FileOutputStream
                out = new BufferedOutputStream(new FileOutputStream(file, append));
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                out = new BufferedOutputStream(
                        Factory.createShellOutputStream(Factory.createShellFile(file), append),
                        4 * 1024 * 1024);
            }
        }
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }
}
