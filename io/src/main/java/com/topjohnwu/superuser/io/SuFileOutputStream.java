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

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IOFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Open {@link OutputStream}s that read files with root access.
 * <p>
 * Directly creating instances of this class is deprecated, please use the static helper
 * methods to open new OutputStreams.
 */
public class SuFileOutputStream extends BufferedOutputStream {

    /**
     * {@code SuFileOutputStream.open(new File(path), false)}
     */
    public static OutputStream open(String path) throws FileNotFoundException {
        return open(new File(path), false);
    }

    /**
     * {@code SuFileOutputStream.open(new File(path), append)}
     */
    public static OutputStream open(String path, boolean append) throws FileNotFoundException {
        return open(new File(path), append);
    }

    /**
     * {@code SuFileOutputStream.open(file, false)}
     */
    public static OutputStream open(File file) throws FileNotFoundException {
        return open(file, false);
    }

    /**
     * Open an {@link OutputStream} with root access.
     * <p>
     * Unless {@code file} is an {@link SuFile}, this method will always try to directly
     * open a {@link FileOutputStream}, and fallback to using root access when it fails.
     * <p>
     * <strong>Root Access Streams:</strong><br>
     * On Android 5.0 and higher (API 21+), internally a named pipe (FIFO) is created
     * to bridge all I/O operations across process boundary, providing 100% native
     * {@link FileOutputStream} performance.
     * A single root command is issued through the main shell.
     * <br>
     * On Android 4.4 and lower, all write operations will be applied to a temporary file in
     * the application cache folder. When the stream is closed, the temporary file
     * will then be copied over to the provided {@code file} at once then deleted.
     * <p>
     * Unlike {@link #openCompat(File, boolean)}, the stream is NOT buffered internally.
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    public static OutputStream open(File file, boolean append) throws FileNotFoundException {
        if (file instanceof SuFile) {
            return root((SuFile) file, append);
        } else {
            try {
                // Try normal FileInputStream
                return new FileOutputStream(file, append);
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                return root(new SuFile(file), append);
            }
        }
    }

    /**
     * {@code SuFileOutputStream.openCompat(new File(path), false)}
     */
    public static OutputStream openCompat(String path) throws FileNotFoundException {
        return openCompat(new File(path), false);
    }

    /**
     * {@code SuFileOutputStream.openCompat(new File(path), append)}
     */
    public static OutputStream openCompat(String path, boolean append) throws FileNotFoundException {
        return openCompat(new File(path), append);
    }

    /**
     * {@code SuFileOutputStream.openCompat(file, false)}
     */
    public static OutputStream openCompat(File file) throws FileNotFoundException {
        return openCompat(file, false);
    }

    /**
     * Open an {@link OutputStream} with root access (compatibility mode).
     * <p>
     * Unless {@code file} is an {@link SuFile}, this method will always try to directly
     * open a {@link FileOutputStream}, and fallback to using root access when it fails.
     * <p>
     * <strong>Root Access Streams:</strong><br>
     * On Android 5.0 and higher (API 21+), this is the same as {@link #open(File, boolean)}, but
     * additionally wrapped with {@link BufferedOutputStream} for consistency.
     * <br>
     * On Android 4.4 and lower, the returned stream will do every I/O operation with {@code dd}
     * commands via the main root shell. This was the implementation in older versions of
     * {@code libsu} and is proven to be error prone, but preserved as "compatibility mode".
     * <p>
     * The returned stream is <b>already buffered</b>, do not add another
     * layer of {@link BufferedOutputStream} to add more overhead!
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    public static OutputStream openCompat(File file, boolean append) throws FileNotFoundException {
        return new BufferedOutputStream(compat(file, append));
    }

    private static OutputStream compat(File file, boolean append) throws FileNotFoundException {
        if (file instanceof SuFile) {
            return shell((SuFile) file, append);
        } else {
            try {
                // Try normal FileInputStream
                return new FileOutputStream(file, append);
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                return shell(new SuFile(file), append);
            }
        }
    }

    private static OutputStream root(SuFile file, boolean append) throws FileNotFoundException {
        if (Build.VERSION.SDK_INT >= 21)
            return IOFactory.fifoOut(file, append);
        else
            return IOFactory.copyOut(file, append);
    }

    private static OutputStream shell(SuFile file, boolean append) throws FileNotFoundException {
        if (Build.VERSION.SDK_INT >= 21)
            return IOFactory.fifoOut(file, append);
        else
            return IOFactory.shellOut(file, append);
    }

    // Deprecated APIs

    /**
     * Same as {@link #openCompat(String)}
     * @deprecated please switch to {@link #open(String)}
     */
    @Deprecated
    public SuFileOutputStream(String path) throws FileNotFoundException {
        this(new File(path), false);
    }

    /**
     * Same as {@link #openCompat(String, boolean)}
     * @deprecated please switch to {@link #open(String, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(String path, boolean append) throws FileNotFoundException {
        this(new File(path), append);
    }

    /**
     * Same as {@link #openCompat(File)}
     * @deprecated please switch to {@link #open(File, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(File file) throws FileNotFoundException {
        this(file, false);
    }

    /**
     * Same as {@link #openCompat(File, boolean)}
     * @deprecated please switch to {@link #open(File, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(compat(file, append));
    }
}
