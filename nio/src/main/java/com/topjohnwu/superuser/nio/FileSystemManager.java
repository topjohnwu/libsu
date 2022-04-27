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

package com.topjohnwu.superuser.nio;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.internal.NIOFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;

/**
 * Access file system APIs.
 */
public abstract class FileSystemManager {

    private static Binder fsService;

    private static final FileSystemManager LOCAL = NIOFactory.createLocal();

    /**
     * Get the service that exposes file system APIs over Binder IPC.
     * <p>
     * Sending the {@link Binder} obtained from this method to a client process enables
     * the calling process to perform file system operations on behalf of the client.
     * This allows a client process to access files normally denied by its permissions.
     * <p>
     * You can pass this {@link Binder} object in many different ways, such as returning it in the
     * {@code onBind()} method of (root) services, passing it around with a {@link Bundle},
     * or returning it in an AIDL interface method. The receiving end will get an {@link IBinder},
     * which should be passed to {@link #getRemote(IBinder)} for usage.
     */
    @NonNull
    public synchronized static Binder getService() {
        if (fsService == null)
            fsService = NIOFactory.createFsService();
        return fsService;
    }

    @NonNull
    public static FileSystemManager getLocal() {
        return LOCAL;
    }

    /**
     * Create a {@link FileSystemManager} to access file system APIs of a remote process.
     * @param binder a remote proxy of the {@link Binder} obtained from {@link #getService()}
     */
    @NonNull
    public static FileSystemManager getRemote(@NonNull IBinder binder) {
        return NIOFactory.createRemote(binder);
    }

    /**
     * @see File#File(String)
     */
    @NonNull
    public abstract ExtendedFile newFile(@NonNull String pathname);

    /**
     * @see File#File(String, String)
     */
    @NonNull
    public abstract ExtendedFile newFile(@Nullable String parent, @NonNull String child);

    /**
     * @see File#File(File, String)
     */
    @NonNull
    public final ExtendedFile newFile(@Nullable File parent, @NonNull String child) {
        return newFile(parent == null ? null : parent.getPath(), child);
    }

    /**
     * @see File#File(URI)
     */
    @NonNull
    public final ExtendedFile newFile(@NonNull URI uri) {
        return newFile(new File(uri).getPath());
    }

    /**
     * Opens a file channel to access the file.
     * @param pathname the file to be opened.
     * @param mode same {@code mode} argument in {@link ParcelFileDescriptor#open(File, int)}
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     * @see ParcelFileDescriptor#open(File, int)
     */
    @NonNull
    public final FileChannel openChannel(@NonNull String pathname, int mode) throws IOException {
        return openChannel(new File(pathname), mode);
    }

    /**
     * Opens a file channel to access the file.
     * @param file the file to be opened.
     * @param mode same {@code mode} argument in {@link ParcelFileDescriptor#open(File, int)}
     * @return a new FileChannel pointing to the given file.
     * @throws IOException if the given file can not be opened with the requested mode.
     * @see ParcelFileDescriptor#open(File, int)
     */
    @NonNull
    public abstract FileChannel openChannel(@NonNull File file, int mode) throws IOException;
}
