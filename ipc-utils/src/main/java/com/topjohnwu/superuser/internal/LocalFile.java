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

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class LocalFile extends FileImpl<LocalFile> {

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

    public LocalFile(String pathname) {
        super(pathname, CREATOR);
    }

    public LocalFile(String parent, String child) {
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
}
