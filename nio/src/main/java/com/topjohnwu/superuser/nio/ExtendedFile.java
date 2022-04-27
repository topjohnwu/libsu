/*
 * Copyright 2022 John "topjohnwu" Wu
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

package com.topjohnwu.superuser.nio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public abstract class ExtendedFile extends File {

    /**
     * {@inheritDoc}
     */
    protected ExtendedFile(@NonNull String pathname) {
        super(pathname);
    }

    /**
     * {@inheritDoc}
     */
    protected ExtendedFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * {@inheritDoc}
     */
    protected ExtendedFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * {@inheritDoc}
     */
    protected ExtendedFile(@NonNull URI uri) {
        super(uri);
    }

    /**
     * @return true if the abstract pathname denotes a block device.
     */
    public abstract boolean isBlock();

    /**
     * @return true if the abstract pathname denotes a character device.
     */
    public abstract boolean isCharacter();

    /**
     * @return true if the abstract pathname denotes a symbolic link.
     */
    public abstract boolean isSymlink();

    /**
     * @return true if the abstract pathname denotes a named pipe (FIFO).
     */
    public abstract boolean isNamedPipe();

    /**
     * @return true if the abstract pathname denotes a socket file.
     */
    public abstract boolean isSocket();

    /**
     * Creates a new hard link named by this abstract pathname of an existing file
     * if and only if a file with this name does not yet exist.
     * @param existing a path to an existing file.
     * @return <code>true</code> if the named file does not exist and was successfully
     *         created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewLink(String existing) throws IOException;

    /**
     * Creates a new symbolic link named by this abstract pathname to a target file
     * if and only if a file with this name does not yet exist.
     * @param target the target of the symbolic link.
     * @return <code>true</code> if the named file does not exist and was successfully
     *         created; <code>false</code> if the named file already exists.
     * @throws IOException if an I/O error occurred.
     */
    public abstract boolean createNewSymlink(String target) throws IOException;

    /**
     * Opens an InputStream with the matching implementation of the file.
     * @see FileInputStream#FileInputStream(File)
     */
    public abstract InputStream openInputStream() throws IOException;

    /**
     * Opens an OutputStream with the matching implementation of the file.
     * @see FileOutputStream#FileOutputStream(File, boolean)
     */
    public abstract OutputStream openOutputStream(boolean append) throws IOException;

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getAbsoluteFile();

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public abstract ExtendedFile getCanonicalFile() throws IOException;

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile getParentFile();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles();

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FilenameFilter filter);

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public abstract ExtendedFile[] listFiles(@Nullable FileFilter filter);
}
