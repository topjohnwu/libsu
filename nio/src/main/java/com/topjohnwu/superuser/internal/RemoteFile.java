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

import static android.os.ParcelFileDescriptor.MODE_APPEND;
import static android.os.ParcelFileDescriptor.MODE_CREATE;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.os.ParcelFileDescriptor.MODE_TRUNCATE;
import static android.os.ParcelFileDescriptor.MODE_WRITE_ONLY;

import android.os.RemoteException;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

class RemoteFile extends FileImpl<RemoteFile> {

    private final IFileSystemService fs;

    private static final Creator<RemoteFile> CREATOR = new Creator<RemoteFile>() {

        @Override
        public RemoteFile[] createArray(int n) {
            return new RemoteFile[n];
        }

        @Override
        public RemoteFile create(RemoteFile src, String path) {
            return new RemoteFile(src.fs, path);
        }

        @Override
        public RemoteFile createChild(RemoteFile parent, String name) {
            return new RemoteFile(parent.fs, parent.getPath(), name);
        }
    };

    RemoteFile(IFileSystemService f, String path) {
        super(path, CREATOR);
        fs = f;
    }

    RemoteFile(IFileSystemService f, String parent, String child) {
        super(parent, child, CREATOR);
        fs = f;
    }

    @Override
    @NonNull
    public String getCanonicalPath() throws IOException {
        try {
            ParcelValues b = fs.getCanonicalPath(getPath());
            IOException ex = b.getTyped(0);
            if (ex != null) {
                throw ex;
            }
            return b.getTyped(1);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    private boolean checkAccess(int access) {
        try {
            return fs.checkAccess(getPath(), access);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean canRead() {
        return checkAccess(OsConstants.R_OK);
    }

    @Override
    public boolean canWrite() {
        return checkAccess(OsConstants.W_OK);
    }

    @Override
    public boolean canExecute() {
        return checkAccess(OsConstants.X_OK);
    }

    @Override
    public boolean exists() {
        return checkAccess(OsConstants.F_OK);
    }

    @Override
    public boolean isDirectory() {
        try {
            return fs.isDirectory(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isFile() {
        try {
            return fs.isFile(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isBlock() {
        try {
            return OsConstants.S_ISBLK(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isCharacter() {
        try {
            return OsConstants.S_ISCHR(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isSymlink() {
        try {
            return OsConstants.S_ISLNK(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isNamedPipe() {
        try {
            return OsConstants.S_ISFIFO(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isSocket() {
        try {
            return OsConstants.S_ISSOCK(fs.getMode(getPath()));
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean isHidden() {
        try {
            return fs.isHidden(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public long lastModified() {
        try {
            return fs.lastModified(getPath());
        } catch (RemoteException e) {
            return Long.MIN_VALUE;
        }
    }

    @Override
    public long length() {
        try {
            return fs.length(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public boolean createNewFile() throws IOException {
        try {
            ParcelValues b = fs.createNewFile(getPath());
            IOException ex = b.getTyped(0);
            if (ex != null) {
                throw ex;
            }
            return b.getTyped(1);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewLink(String existing) throws IOException {
        try {
            ParcelValues b = fs.createLink(getPath(), existing, false);
            IOException ex = b.getTyped(0);
            if (ex != null) {
                throw ex;
            }
            return b.getTyped(1);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewSymlink(String target) throws IOException {
        try {
            ParcelValues b = fs.createLink(getPath(), target, true);
            IOException ex = b.getTyped(0);
            if (ex != null) {
                throw ex;
            }
            return b.getTyped(1);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean delete() {
        try {
            return fs.delete(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public void deleteOnExit() {
        throw new UnsupportedOperationException("deleteOnExit() is not supported in RemoteFile");
    }

    @Override
    public String[] list() {
        try {
            return fs.list(getPath());
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public boolean mkdir() {
        try {
            return fs.mkdir(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean mkdirs() {
        try {
            return fs.mkdirs(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean renameTo(@NonNull File dest) {
        try {
            return fs.renameTo(getPath(), dest.getAbsolutePath());
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setLastModified(long time) {
        try {
            return fs.setLastModified(getPath(), time);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setReadOnly() {
        try {
            return fs.setReadOnly(getPath());
        } catch (RemoteException e) {
            return false;
        }
    }

    private boolean setPermission(int access, boolean enable, boolean ownerOnly) {
        try {
            return fs.setPermission(getPath(), access, enable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return setPermission(0x2, writable, ownerOnly);
    }

    @Override
    public boolean setWritable(boolean writable) {
        return setPermission(0x2, writable, true);
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return setPermission(0x4, readable, ownerOnly);
    }

    @Override
    public boolean setReadable(boolean readable) {
        return setPermission(0x4, readable, true);
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return setPermission(0x1, executable, ownerOnly);
    }

    @Override
    public boolean setExecutable(boolean executable) {
        return setPermission(0x1, executable, true);
    }

    @Override
    public long getTotalSpace() {
        try {
            return fs.getTotalSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public long getFreeSpace() {
        try {
            return fs.getFreeSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public long getUsableSpace() {
        try {
            return fs.getUsableSpace(getPath());
        } catch (RemoteException e) {
            return 0L;
        }
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return Channels.newInputStream(new RemoteFileChannel(fs, this, MODE_READ_ONLY));
    }

    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        int mode = MODE_WRITE_ONLY | MODE_CREATE;
        if (append)
            mode |= MODE_APPEND;
        else
            mode |= MODE_TRUNCATE;
        return Channels.newOutputStream(new RemoteFileChannel(fs, this, mode));
    }
}
