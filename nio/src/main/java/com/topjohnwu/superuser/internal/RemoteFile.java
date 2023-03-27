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

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class RemoteFile extends FileImpl<RemoteFile> {

    private final IFileSystemService fs;

    RemoteFile(IFileSystemService f, String path) {
        super(path);
        fs = f;
    }

    RemoteFile(IFileSystemService f, String parent, String child) {
        super(parent, child);
        fs = f;
    }

    @Override
    protected RemoteFile create(String path) {
        return new RemoteFile(fs, path);
    }

    @NonNull
    @Override
    public RemoteFile getChildFile(String name) {
        return new RemoteFile(fs, getPath(), name);
    }

    @Override
    protected RemoteFile[] createArray(int n) {
        return new RemoteFile[n];
    }

    @Override
    @NonNull
    public String getCanonicalPath() throws IOException {
        try {
            return fs.getCanonicalPath(getPath()).tryAndGet();
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
            return fs.createNewFile(getPath()).tryAndGet();
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewLink(String existing) throws IOException {
        try {
            return fs.createLink(getPath(), existing, false).tryAndGet();
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean createNewSymlink(String target) throws IOException {
        try {
            return fs.createLink(getPath(), target, true).tryAndGet();
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

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        try {
            return fs.setWritable(getPath(), writable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        try {
            return fs.setReadable(getPath(), readable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        try {
            return fs.setExecutable(getPath(), executable, ownerOnly);
        } catch (RemoteException e) {
            return false;
        }
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

    @NonNull
    @Override
    public InputStream newInputStream() throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            fs.openReadStream(getPath(), pipe[1]).checkException();
        } catch (RemoteException e) {
            pipe[0].close();
            throw new IOException(e);
        } finally {
            pipe[1].close();
        }
        return new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
    }

    @NonNull
    @Override
    public OutputStream newOutputStream(boolean append) throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            fs.openWriteStream(getPath(), pipe[0], append).checkException();
        } catch (RemoteException e) {
            pipe[1].close();
            throw new IOException(e);
        } finally {
            pipe[0].close();
        }
        return new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
    }
}
