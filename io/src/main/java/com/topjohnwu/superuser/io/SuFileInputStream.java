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

package com.topjohnwu.superuser.io;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.internal.IOFactory;
import com.topjohnwu.superuser.internal.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Open {@link InputStream}s that read files with root access.
 * <p>
 * Directly creating instances of this class is deprecated, please use the static helper
 * methods to open new InputStreams.
 */
public final class SuFileInputStream {

    /**
     * {@code SuFileInputStream.open(new File(path))}
     */
    @NonNull
    public static InputStream open(@NonNull String path) throws FileNotFoundException {
        return open(new File(path));
    }

    /**
     * Open an {@link InputStream} with root access.
     * <p>
     * Unless {@code file} is an {@link SuFile}, this method will always try to directly
     * open a {@link FileInputStream}, and fallback to using root access when it fails.
     * @see FileInputStream#FileInputStream(File)
     */
    @NonNull
    public static InputStream open(@NonNull File file) throws FileNotFoundException {
        if (file instanceof SuFile) {
            return IOFactory.fifoIn((SuFile) file);
        } else {
            try {
                // Try normal FileInputStream
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                if (!Utils.isMainShellRoot())
                    throw e;
                return IOFactory.fifoIn(new SuFile(file));
            }
        }
    }

    private SuFileInputStream() {}
}
