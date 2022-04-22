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

package com.topjohnwu.superuser.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;

public abstract class ExtendedFile extends File {

    /**
     * {@inheritDoc}
     */
    public ExtendedFile(@NonNull String pathname) {
        super(pathname);
    }

    /**
     * {@inheritDoc}
     */
    public ExtendedFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * {@inheritDoc}
     */
    public ExtendedFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
    }

    /**
     * {@inheritDoc}
     */
    public ExtendedFile(@NonNull URI uri) {
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
     * @return true if the abstract pathname denotes a symbolic link file.
     */
    public abstract boolean isSymlink();

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
