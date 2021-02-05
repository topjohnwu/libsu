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
    @NonNull
    public static OutputStream open(@NonNull String path) throws FileNotFoundException {
        return open(new File(path), false);
    }

    /**
     * {@code SuFileOutputStream.open(new File(path), append)}
     */
    @NonNull
    public static OutputStream open(@NonNull String path, boolean append) throws FileNotFoundException {
        return open(new File(path), append);
    }

    /**
     * {@code SuFileOutputStream.open(file, false)}
     */
    @NonNull
    public static OutputStream open(@NonNull File file) throws FileNotFoundException {
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
     * A single root command is issued through the main shell at stream construction.
     * <br>
     * On Android 4.4 and lower, all write operations will be applied to a temporary file in
     * the application cache folder. When the stream is closed, the temporary file
     * will be copied over to the provided {@code file} by using a single {@code cat}
     * command with the main shell, then deleted.
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    @NonNull
    public static OutputStream open(@NonNull File file, boolean append) throws FileNotFoundException {
        if (file instanceof SuFile) {
            return fifo((SuFile) file, append);
        } else {
            try {
                // Try normal FileInputStream
                return new FileOutputStream(file, append);
            } catch (FileNotFoundException e) {
                if (!Shell.rootAccess())
                    throw e;
                return fifo(new SuFile(file), append);
            }
        }
    }

    /**
     * {@code SuFileOutputStream.openNoCopy(new File(path), false)}
     */
    @NonNull
    public static OutputStream openNoCopy(@NonNull String path) throws FileNotFoundException {
        return openNoCopy(new File(path), false);
    }

    /**
     * {@code SuFileOutputStream.openNoCopy(new File(path), append)}
     */
    @NonNull
    public static OutputStream openNoCopy(@NonNull String path, boolean append) throws FileNotFoundException {
        return openNoCopy(new File(path), append);
    }

    /**
     * {@code SuFileOutputStream.openNoCopy(file, false)}
     */
    @NonNull
    public static OutputStream openNoCopy(@NonNull File file) throws FileNotFoundException {
        return openNoCopy(file, false);
    }

    /**
     * Open an {@link OutputStream} with root access (no internal copying).
     * <p>
     * <strong>If your minSdkVersion is 21 or higher, this method is irrelevant.</strong>
     * <p>
     * Unless {@code file} is an {@link SuFile}, this method will always try to directly
     * open a {@link FileOutputStream}, and fallback to using root access when it fails.
     * <p>
     * <strong>Root Access Streams:</strong><br>
     * On Android 5.0 and higher (API 21+), this is equivalent to {@link #open(File, boolean)}.
     * <br>
     * On Android 4.4 and lower, the returned stream will do every write operation with a
     * {@code dd} command via the main root shell. <strong>Writing to files through shell
     * commands is proven to be error prone. YOU HAVE BEEN WARNED!</strong>
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    @NonNull
    public static OutputStream openNoCopy(@NonNull File file, boolean append) throws FileNotFoundException {
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

    private static OutputStream fifo(SuFile file, boolean append) throws FileNotFoundException {
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
     * Same as {@link #openNoCopy(String)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(String)}
     */
    @Deprecated
    public SuFileOutputStream(String path) throws FileNotFoundException {
        super(openNoCopy(path, false));
    }

    /**
     * Same as {@link #openNoCopy(String, boolean)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(String, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(String path, boolean append) throws FileNotFoundException {
        super(openNoCopy(path, append));
    }

    /**
     * Same as {@link #openNoCopy(File)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(File, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(File file) throws FileNotFoundException {
        super(openNoCopy(file, false));
    }

    /**
     * Same as {@link #openNoCopy(File, boolean)}, but guaranteed to be buffered internally to
     * match backwards compatibility behavior.
     * @deprecated please switch to {@link #open(File, boolean)}
     */
    @Deprecated
    public SuFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(openNoCopy(file, append));
    }
}
