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

package com.topjohnwu.superuser.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.nio.ExtendedFile;

import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

abstract class FileImpl<T extends ExtendedFile> extends ExtendedFile {

    protected FileImpl(String pathname) {
        super(pathname);
    }

    protected FileImpl(String parent, String child) {
        super(parent, child);
    }

    protected abstract T create(String path);
    protected abstract T[] createArray(int n);
    @NonNull
    @Override
    public abstract T getChildFile(String name);

    @NonNull
    @Override
    public T getAbsoluteFile() {
        return create(getAbsolutePath());
    }

    @NonNull
    @Override
    public T getCanonicalFile() throws IOException {
        return create(getCanonicalPath());
    }

    @Nullable
    @Override
    public T getParentFile() {
        return create(getParent());
    }

    @Nullable
    @Override
    public T[] listFiles() {
        String[] ss = list();
        if (ss == null)
            return null;
        int n = ss.length;
        T[] fs = createArray(n);
        for (int i = 0; i < n; i++) {
            fs[i] = getChildFile(ss[i]);
        }
        return fs;
    }

    @Nullable
    @Override
    public T[] listFiles(@Nullable FilenameFilter filter) {
        String[] ss = list();
        if (ss == null)
            return null;
        ArrayList<T> files = new ArrayList<>();
        for (String s : ss) {
            if ((filter == null) || filter.accept(this, s))
                files.add(getChildFile(s));
        }
        return files.toArray(createArray(0));
    }

    @Nullable
    @Override
    public T[] listFiles(@Nullable FileFilter filter) {
        String[] ss = list();
        if (ss == null)
            return null;
        ArrayList<T> files = new ArrayList<>();
        for (String s : ss) {
            T f = getChildFile(s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(createArray(0));
    }
}
