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
import android.util.SparseArray;

import androidx.annotation.NonNull;

import java.io.IOException;

class FileContainer {

    private final static String ERROR_MSG = "Requested file was not opened!";

    private int nextHandle = 0;
    // pid -> handle -> holder
    private final SparseArray<SparseArray<OpenFile>> files = new SparseArray<>();

    @NonNull
    synchronized OpenFile get(int handle) throws IOException {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            throw new IOException(ERROR_MSG);
        OpenFile h = pidFiles.get(handle);
        if (h == null)
            throw new IOException(ERROR_MSG);
        return h;
    }

    synchronized int put(OpenFile h) {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null) {
            pidFiles = new SparseArray<>();
            files.put(pid, pidFiles);
        }
        int handle = nextHandle++;
        pidFiles.append(handle, h);
        return handle;
    }

    synchronized void remove(int handle) {
        int pid = Binder.getCallingPid();
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        OpenFile h = pidFiles.get(handle);
        if (h == null)
            return;
        pidFiles.remove(handle);
        synchronized (h) {
            h.close();
        }
    }

    synchronized void pidDied(int pid) {
        SparseArray<OpenFile> pidFiles = files.get(pid);
        if (pidFiles == null)
            return;
        files.remove(pid);
        for (int i = 0; i < pidFiles.size(); ++i) {
            pidFiles.valueAt(i).close();
        }
    }
}
