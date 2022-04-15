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

package com.topjohnwu.superuser.ipc.utils;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.internal.IFileSystemService;

import java.io.File;
import java.io.IOException;

/**
 * Expose filesystem APIs over Binder.
 *
 * In the remote process, create a new {@link Binder} object that exposes filesystem APIs by
 * calling {@link #createBinder()}. You can then pass this {@link Binder} object in many different
 * ways, such as adding it to an Intent, sending it with {@link Message}es, returning it in the
 * {@code onBind()} of bind / root services, or returning it in an AIDL interface.
 *
 * In the client process, create a {@link Remote} instance by passing the remote {@link IBinder}
 * proxy you received to {@link #asRemote(IBinder)}. This {@link Remote} instance can then be used
 * to construct remote I/O classes, such as {@link RemoteFile}
 */
public final class FileSystem {

    // The actual reason why this class exists is because we do not want to expose
    // IFileSystemService in any API surface to keep it an internal implementation detail.

    private FileSystem() {}

    /**
     * Create a new {@link Binder} instance that exposes filesystem APIs.
     * An example use case is to return this value in {@code onBind()} of (root) services.
     */
    @NonNull
    public static Binder createBinder() {
        return new Impl();
    }

    /**
     * Create a {@link Remote} instance that exposes filesystem APIs of a remote process.
     * @param service an instance or a remote proxy of the return value of {@link #createBinder()}
     * @return this return value is for constructing {@link RemoteFile}
     */
    @NonNull
    public static Remote asRemote(@NonNull IBinder service) {
        return new Remote(service);
    }

    /**
     * Represents the filesystem API of a remote process.
     * @see #asRemote(IBinder)
     */
    public static class Remote {
        final IFileSystemService fs;
        Remote(IBinder b) {
            fs = IFileSystemService.Stub.asInterface(b);
        }
    }

    static class Impl extends IFileSystemService.Stub {

        private final LruCache<String, File> mCache = new LruCache<String, File>(100) {
            @Override
            protected File create(String key) {
                return new File(key);
            }
        };

        @Override
        public Bundle getCanonicalPath(String path) {
            Bundle b = new Bundle();
            try {
                b.putString("result", mCache.get(path).getCanonicalPath());
            } catch (IOException e) {
                b.putString("error", e.getMessage());
            }
            return b;
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
        public Bundle createNewFile(String path) {
            Bundle b = new Bundle();
            try {
                b.putBoolean("result", mCache.get(path).createNewFile());
            } catch (IOException e) {
                b.putString("error", e.getMessage());
            }
            return b;
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
    }
}
