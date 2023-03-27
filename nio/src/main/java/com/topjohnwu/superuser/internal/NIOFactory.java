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

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.nio.ExtendedFile;
import com.topjohnwu.superuser.nio.FileSystemManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class NIOFactory {

    private NIOFactory() {}

    public static FileSystemManager createLocal() {
        return new FileSystemManager() {
            @NonNull
            @Override
            public ExtendedFile getFile(@NonNull String pathname) {
                return new LocalFile(pathname);
            }

            @NonNull
            @Override
            public ExtendedFile getFile(@Nullable String parent, @NonNull String child) {
                return new LocalFile(parent, child);
            }

            @NonNull
            @Override
            public FileChannel openChannel(@NonNull File file, int mode) throws IOException {
                if (Build.VERSION.SDK_INT >= 26) {
                    return FileChannel.open(file.toPath(), FileUtils.modeToOptions(mode));
                } else {
                    FileUtils.Flag f = FileUtils.modeToFlag(mode);
                    if (f.write) {
                        if (!f.create) {
                            if (!file.exists()) {
                                ErrnoException e = new ErrnoException("open", OsConstants.ENOENT);
                                throw new FileNotFoundException(file + ": " + e.getMessage());
                            }
                        }
                        if (f.append) {
                            return new FileOutputStream(file, true).getChannel();
                        }
                        if (!f.read && f.truncate) {
                            return new FileOutputStream(file, false).getChannel();
                        }

                        // Unfortunately, there is no way to create a write-only channel
                        // without truncating. Forced to open rw RAF in all cases.
                        FileChannel ch = new RandomAccessFile(file, "rw").getChannel();
                        if (f.truncate) {
                            ch.truncate(0);
                        }
                        return ch;
                    } else {
                        return new FileInputStream(file).getChannel();
                    }
                }
            }
        };
    }

    public static FileSystemManager createRemote(IBinder b) throws RemoteException {
        IFileSystemService fs = IFileSystemService.Stub.asInterface(b);
        if (fs == null)
            throw new IllegalArgumentException("The IBinder provided is invalid");

        fs.register(new Binder());
        return new FileSystemManager() {
            @NonNull
            @Override
            public ExtendedFile getFile(@NonNull String pathname) {
                return new RemoteFile(fs, pathname);
            }

            @NonNull
            @Override
            public ExtendedFile getFile(@Nullable String parent, @NonNull String child) {
                return new RemoteFile(fs, parent, child);
            }

            @NonNull
            @Override
            public FileChannel openChannel(@NonNull File file, int mode) throws IOException {
                return new RemoteFileChannel(fs, file, mode);
            }
        };
    }

    public static FileSystemService createFsService() {
        return new FileSystemService();
    }
}
