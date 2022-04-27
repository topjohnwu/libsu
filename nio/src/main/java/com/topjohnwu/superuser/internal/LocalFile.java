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

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class LocalFile extends FileImpl<LocalFile> {

    private static final Creator<LocalFile> CREATOR = new Creator<LocalFile>() {

        @Override
        public LocalFile[] createArray(int n) {
            return new LocalFile[n];
        }

        @Override
        public LocalFile create(LocalFile src, String path) {
            return new LocalFile(path);
        }

        @Override
        public LocalFile createChild(LocalFile parent, String name) {
            return new LocalFile(parent.getPath(), name);
        }
    };

    LocalFile(String pathname) {
        super(pathname, CREATOR);
    }

    LocalFile(String parent, String child) {
        super(parent, child, CREATOR);
    }

    @Override
    public boolean isBlock() {
        try {
            StructStat st = Os.lstat(getPath());
            return OsConstants.S_ISBLK(st.st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isCharacter() {
        try {
            StructStat st = Os.lstat(getPath());
            return OsConstants.S_ISCHR(st.st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isSymlink() {
        try {
            StructStat st = Os.lstat(getPath());
            return OsConstants.S_ISLNK(st.st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isNamedPipe() {
        try {
            StructStat st = Os.lstat(getPath());
            return OsConstants.S_ISFIFO(st.st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean isSocket() {
        try {
            StructStat st = Os.lstat(getPath());
            return OsConstants.S_ISSOCK(st.st_mode);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(this);
    }

    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        return new FileOutputStream(this, append);
    }

    @Override
    public boolean createNewLink(String existing) throws IOException {
        return createLink(existing, false);
    }

    @Override
    public boolean createNewSymlink(String target) throws IOException {
        return createLink(target, true);
    }

    private boolean createLink(String target, boolean soft) throws IOException {
        try {
            if (soft)
                Os.symlink(target, getPath());
            else
                Os.link(target, getPath());
            return true;
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EEXIST) {
                throw new IOException(e);
            }
            return false;
        }
    }
}
