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

import com.topjohnwu.superuser.internal.Factory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;

/**
 * An {@link java.io.InputStream} that read files using the global shell instance.
 * <p>
 * This class always checks whether using a shell is necessary. If not, it simply opens a new
 * {@link FileInputStream}.
 * <p>
 * Note: this class is <b>always buffered internally</b>, do not add another layer of
 * {@link BufferedInputStream} to add more overhead!
 */
public class SuFileInputStream extends FilterInputStream {

    /**
     * @see FileInputStream#FileInputStream(String)
     */
    public SuFileInputStream(String path) throws FileNotFoundException {
        this(new SuFile(path));
    }

    /**
     * @see FileInputStream#FileInputStream(File)
     */
    public SuFileInputStream(File file) throws FileNotFoundException {
        super(null);
        SuFile f;
        if (file instanceof SuFile)
            f = (SuFile) file;
        else
            f = new SuFile(file);
        if (f.useShell()) {
            // Use shell file io
            in = Factory.createShellInputStream(f);
        } else {
            // Normal file input
            in = new BufferedInputStream(new FileInputStream(f));
        }
    }
}
