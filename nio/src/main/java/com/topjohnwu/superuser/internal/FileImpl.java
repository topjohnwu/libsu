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

package com.topjohnwu.superuser.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.nio.ExtendedFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class FileImpl<T extends ExtendedFile> extends ExtendedFile {

    private final Creator<T> c;

    protected FileImpl(String pathname, Creator<T> creator) {
        super(pathname);
        c = creator;
    }

    protected FileImpl(String parent, String child, Creator<T> creator) {
        super(parent, child);
        c = creator;
    }

    @SuppressWarnings("unchecked")
    private T asT() {
        return (T) this;
    }

    @NonNull
    @Override
    public T getAbsoluteFile() {
        return c.create(asT(), getAbsolutePath());
    }

    @NonNull
    @Override
    public T getCanonicalFile() throws IOException {
        return c.create(asT(), getCanonicalPath());
    }

    @Nullable
    @Override
    public T getParentFile() {
        return c.create(asT(), getParent());
    }

    @Nullable
    @Override
    public T[] listFiles() {
        String[] ss = list();
        if (ss == null)
            return null;
        int n = ss.length;
        T[] fs = c.createArray(n);
        for (int i = 0; i < n; i++) {
            fs[i] = c.createChild(asT(), ss[i]);
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
                files.add(c.createChild(asT(), s));
        }
        return files.toArray(c.createArray(0));
    }

    @Nullable
    @Override
    public T[] listFiles(@Nullable FileFilter filter) {
        String[] ss = list();
        if (ss == null)
            return null;
        ArrayList<T> files = new ArrayList<>();
        for (String s : ss) {
            T f = c.createChild(asT(), s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(c.createArray(0));
    }

    protected interface Creator<T extends File> {
        T create(T src, String pathname);
        T createChild(T parent, String name);
        T[] createArray(int n);
    }
}
