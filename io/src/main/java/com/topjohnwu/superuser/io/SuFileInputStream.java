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

import android.os.Build;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IOFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * Open {@link InputStream}s that read files with root access.
 * <p>
 * Directly creating instances of this class is deprecated, please use the static helper
 * methods to open new InputStreams.
 */
public class SuFileInputStream extends FilterInputStream {

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
     * <p>
     * <strong>Root Access Streams:</strong><br>
     * On Android 5.0 and higher (API 21+), internally a named pipe (FIFO) is created
     * to bridge all I/O operations across process boundary, providing 100% native
     * {@link FileInputStream} performance.
     * A single root command is issued through the main shell at stream construction.
     * <br>
     * On Android 4.4 and lower, the returned stream will do I/O operations using {@code dd}
     * commands via the main root shell for each 4MB chunk. Due to excessive internal buffering,
     * the performance is on par with native streams.
     * @see FileInputStream#FileInputStream(File)
     */
    @NonNull
    public static InputStream open(@NonNull File file) throws FileNotFoundException {
        if (file instanceof SuFile) {
            return root((SuFile) file);
        } else {
            try {
                // Try normal FileInputStream
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                return root(new SuFile(file));
            }
        }
    }

    private static InputStream root(SuFile file) throws FileNotFoundException {
        if (Build.VERSION.SDK_INT >= 21)
            return IOFactory.fifoIn(file);
        else
            return IOFactory.shellIn(file);
    }

    // Deprecated APIs

    /**
     * Same as {@link #open(String)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(String)}
     */
    @Deprecated
    public SuFileInputStream(String path) throws FileNotFoundException {
        this(new File(path));
    }

    /**
     * Same as {@link #open(File)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(File)}
     */
    @Deprecated
    public SuFileInputStream(File file) throws FileNotFoundException {
        super(null);
        if (file instanceof SuFile) {
            in = compat((SuFile) file);
        } else {
            try {
                // Try normal FileInputStream
                in = new BufferedInputStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                in = compat(new SuFile(file));
            }
        }
    }

    private static InputStream compat(SuFile file) throws FileNotFoundException {
        if (Build.VERSION.SDK_INT >= 21)
            return new BufferedInputStream(IOFactory.fifoIn(file));
        else
            return IOFactory.shellIn(file);
    }
}
