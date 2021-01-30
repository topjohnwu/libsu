/*
 * Copyright 2021 John "topjohnwu" Wu
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

package com.topjohnwu.libsuexample;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.ipc.RootService;

import static com.topjohnwu.libsuexample.MainActivity.TAG;

// Demonstrate RootService using AIDL (daemon mode)
class AIDLService extends RootService {
    static {
        // Only load the library when this class is loaded in a root process.
        // The classloader will load this class (and call this static block) in the non-root
        // process because we accessed it when constructing the Intent to send.
        // Add this check so we don't unnecessarily load native code that'll never be used.
        if (Process.myUid() == 0)
            System.loadLibrary("native-lib");
    }

    // Demonstrate we can also run native code via JNI with RootServices
    native int nativeGetUid();
    native String nativeReadFile(String file);

    class TestIPC extends ITestService.Stub {
        @Override
        public int getPid() {
            return Process.myPid();
        }

        @Override
        public int getUid() {
            return nativeGetUid();
        }

        @Override
        public String readCmdline() {
            // Normally we cannot read /proc/cmdline without root
            return nativeReadFile("/proc/cmdline");
        }
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        // This callback will be called when we are reusing a previously started root process
        Log.d(TAG, "AIDLService: onRebind, daemon process reused");
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "AIDLService: onBind");
        return new TestIPC();
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        Log.d(TAG, "AIDLService: onUnbind, client process unbound");
        // We return true here to tell libsu that we want this service to run as a daemon
        return true;
    }
}
