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

import static android.system.OsConstants.O_NONBLOCK;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_WRONLY;
import static android.system.OsConstants.SEEK_CUR;
import static android.system.OsConstants.SEEK_END;
import static android.system.OsConstants.SEEK_SET;

import android.annotation.SuppressLint;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Int64Ref;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.LruCache;
import android.util.MutableLong;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;

class FileSystemService extends IFileSystemService.Stub {

    // This is only for testing purpose
    private static final boolean FORCE_NO_SPLICE = false;

    private static final int PIPE_CAPACITY = 16 * 4096;

    private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
        @Override
        protected File create(String key) {
            return new File(key);
        }
    };

    @Override
    public ParcelValues getCanonicalPath(String path) {
        ParcelValues p = new ParcelValues();
        try {
            String v = mCache.get(path).getCanonicalPath();
            p.add(null);
            p.add(v);
        } catch (IOException e) {
            p.add(e);
            p.add(null);
        }
        return p;
    }

    @Override
    public boolean isDirectory(String path) {
        return mCache.get(path).isDirectory();
    }

    @Override
    public boolean isFile(String path) {
        return mCache.get(path).isFile();
    }

    @Override
    public boolean isHidden(String path) {
        return mCache.get(path).isHidden();
    }

    @Override
    public long lastModified(String path) {
        return mCache.get(path).lastModified();
    }

    @Override
    public long length(String path) {
        return mCache.get(path).length();
    }

    @Override
    public ParcelValues createNewFile(String path) {
        ParcelValues p = new ParcelValues();
        try {
            boolean v = mCache.get(path).createNewFile();
            p.add(null);
            p.add(v);
        } catch (IOException e) {
            p.add(e);
            p.add(null);
        }
        return p;
    }

    @Override
    public boolean delete(String path) {
        return mCache.get(path).delete();
    }

    @Override
    public String[] list(String path) {
        return mCache.get(path).list();
    }

    @Override
    public boolean mkdir(String path) {
        return mCache.get(path).mkdir();
    }

    @Override
    public boolean mkdirs(String path) {
        return mCache.get(path).mkdirs();
    }

    @Override
    public boolean renameTo(String path, String dest) {
        return mCache.get(path).renameTo(mCache.get(dest));
    }

    @Override
    public boolean setLastModified(String path, long time) {
        return mCache.get(path).setLastModified(time);
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public boolean setPermission(String path, int access, boolean enable, boolean ownerOnly) {
        try {
            access = access & 07;
            int mask = ownerOnly ? (access << 6) : (access | (access << 3) | (access << 6));
            StructStat st = Os.stat(path);
            int mode = st.st_mode & 07777;
            Os.chmod(path, enable ? (mode | mask) : (mode & ~mask));
            return true;
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public boolean setReadOnly(String path) {
        return mCache.get(path).setReadOnly();
    }

    @Override
    public boolean checkAccess(String path, int access) {
        try {
            return Os.access(path, access);
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public long getTotalSpace(String path) {
        return mCache.get(path).getTotalSpace();
    }

    @Override
    public long getFreeSpace(String path) {
        return mCache.get(path).getFreeSpace();
    }

    @SuppressLint("UsableSpace")
    @Override
    public long getUsableSpace(String path) {
        return mCache.get(path).getUsableSpace();
    }

    @Override
    public int getMode(String path) {
        try {
            return Os.lstat(path).st_mode;
        } catch (ErrnoException e) {
            return 0;
        }
    }

    @Override
    public ParcelValues createLink(String link, String target, boolean soft) {
        ParcelValues p = new ParcelValues();
        try {
            if (soft)
                Os.symlink(target, link);
            else
                Os.link(target, link);
            p.add(null);
            p.add(true);
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EEXIST) {
                p.add(null);
            } else {
                p.add(new IOException(e));
            }
            p.add(false);
        }
        return p;
    }

    // I/O APIs

    static class FileHolder implements Closeable {
        FileDescriptor fd;
        FileDescriptor read;
        FileDescriptor write;

        private ByteBuffer buf;
        private StructStat st;

        @Override
        public void close() {
            if (fd != null) {
                try { Os.close(fd); } catch (ErrnoException ignored) {}
                fd = null;
            }
            if (read != null) {
                try { Os.close(read); } catch (ErrnoException ignored) {}
                read = null;
            }
            if (write != null) {
                try { Os.close(write); } catch (ErrnoException ignored) {}
                write = null;
            }
        }

        void ensureOpen() throws ClosedChannelException {
            if (fd == null || read == null || write == null)
                throw new ClosedChannelException();
        }

        synchronized ByteBuffer getBuf() {
            if (buf == null)
                buf = ByteBuffer.allocateDirect(PIPE_CAPACITY);
            buf.clear();
            return buf;
        }

        synchronized StructStat getStat() throws ErrnoException {
            if (st == null)
                st = Os.fstat(fd);
            return st;
        }
    }

    static class FileNotOpenException extends IOException {
        FileNotOpenException() {
            super("Requested file was not opened!");
        }
    }

    private final AtomicInteger nextHandle = new AtomicInteger(0);
    private final SparseArray<FileHolder> openFiles = new SparseArray<>();

    @NonNull
    private FileHolder getHolder(int handle) throws FileNotOpenException {
        synchronized (openFiles) {
            FileHolder h = openFiles.get(handle);
            if (h == null) {
                throw new FileNotOpenException();
            }
            return h;
        }
    }

    @SuppressWarnings("OctalInteger")
    @Override
    public ParcelValues open(String path, int mode, String fifo) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        int handle = nextHandle.getAndIncrement();
        FileHolder h = new FileHolder();
        try {
            h.fd = Os.open(path, FileUtils.pfdModeToPosix(mode) | O_NONBLOCK, 0666);
            h.read = Os.open(fifo, O_RDONLY | O_NONBLOCK, 0);
            h.write = Os.open(fifo, O_WRONLY | O_NONBLOCK, 0);
            synchronized (openFiles) {
                openFiles.append(handle, h);
            }
            values.add(handle);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
            h.close();
        }
        return values;
    }

    @Override
    public void close(int handle) {
        final FileHolder h;
        synchronized (openFiles) {
            h = openFiles.get(handle);
            if (h == null)
                return;
            openFiles.remove(handle);
        }
        synchronized (h) {
            h.close();
        }
    }

    @Override
    public ParcelValues pread(int handle, int len, long offset) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            final long result;
            synchronized (h) {
                h.ensureOpen();
                if (!FORCE_NO_SPLICE && Build.VERSION.SDK_INT >= 28) {
                    Int64Ref inOff = offset < 0 ? null : new Int64Ref(offset);
                    result = FileUtils.splice(h.fd, inOff, h.write, null, len, 0);
                } else {
                    StructStat st = h.getStat();
                    if (OsConstants.S_ISREG(st.st_mode) || OsConstants.S_ISBLK(st.st_mode)) {
                        // sendfile only supports reading from mmap-able files
                        MutableLong inOff = offset < 0 ? null : new MutableLong(offset);
                        result = FileUtils.sendfile(h.write, h.fd, inOff, len);
                    } else {
                        // Fallback to copy into internal buffer
                        ByteBuffer buf = h.getBuf();
                        buf.limit(Math.min(len, buf.capacity()));
                        if (offset < 0) {
                            Os.read(h.fd, buf);
                        } else {
                            Os.pread(h.fd, buf, offset);
                        }
                        buf.flip();
                        result = buf.remaining();
                        // Need to write all bytes
                        for (int sz = (int) result; sz > 0;) {
                            sz -= Os.write(h.write, buf);
                        }
                    }
                }
            }
            values.add((int) result);
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }

    @Override
    public ParcelValues pwrite(int handle, int len, long offset) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            synchronized (h) {
                h.ensureOpen();
                if (!FORCE_NO_SPLICE && Build.VERSION.SDK_INT >= 28) {
                    Int64Ref outOff = offset < 0 ? null : new Int64Ref(offset);
                    int sz = len;
                    while (sz > 0) {
                        // Need to write exactly len bytes
                        sz -= FileUtils.splice(h.read, null, h.fd, outOff, sz, 0);
                    }
                } else {
                    // Unfortunately, sendfile does not allow reading from pipes.
                    // Manually read into an internal buffer then write to output.
                    ByteBuffer buf = h.getBuf();
                    int sz = 0;
                    buf.limit(len);
                    // Need to read and write exactly len bytes
                    while (len > sz) {
                        sz += Os.read(h.read, buf);
                    }
                    buf.flip();
                    while (sz > 0) {
                        if (offset < 0) {
                            sz -= Os.write(h.fd, buf);
                        } else {
                            int w = Os.pwrite(h.fd, buf, offset);
                            sz -= w;
                            offset += w;
                        }
                    }
                }
            }
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }

    @Override
    public ParcelValues lseek(int handle, long offset, int whence) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            synchronized (h) {
                h.ensureOpen();
                values.add(Os.lseek(h.fd, offset, whence));
            }
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }

    @Override
    public ParcelValues size(int handle) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            synchronized (h) {
                h.ensureOpen();
                long cur = Os.lseek(h.fd, 0, SEEK_CUR);
                Os.lseek(h.fd, 0, SEEK_END);
                values.add(Os.lseek(h.fd, 0, SEEK_CUR));
                Os.lseek(h.fd, cur, SEEK_SET);
            }
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }

    @Override
    public ParcelValues ftruncate(int handle, long length) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            synchronized (h) {
                h.ensureOpen();
                Os.ftruncate(h.fd, length);
            }
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }

    @Override
    public ParcelValues sync(int handle, boolean metaData) {
        ParcelValues values = new ParcelValues();
        values.add(null);
        try {
            final FileHolder h = getHolder(handle);
            synchronized (h) {
                h.ensureOpen();
                if (metaData)
                    Os.fsync(h.fd);
                else
                    Os.fdatasync(h.fd);
            }
        } catch (IOException e) {
            values.set(0, e);
        } catch (ErrnoException e) {
            values.set(0, new IOException(e));
        }
        return values;
    }
}
