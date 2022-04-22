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
import android.os.IBinder;
import android.os.Message;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.internal.FileSystemImpl;
import com.topjohnwu.superuser.internal.IFileSystemService;

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
public final class FileSystemApi {

    // The actual reason why this class exists is because we do not want to expose
    // IFileSystemService in any API surface to keep it an internal implementation detail.

    private FileSystemApi() {}

    /**
     * Create a new {@link Binder} instance that exposes filesystem APIs.
     * An example use case is to return this value in {@code onBind()} of (root) services.
     */
    @NonNull
    public static Binder createBinder() {
        return new FileSystemImpl();
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

}
