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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.internal.Utils;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

import static com.topjohnwu.libsuexample.MainActivity.TAG;

// Demonstrate root service using Messengers
class MSGService extends RootService implements Handler.Callback {

    static final int MSG_GETINFO = 1;
    static final String CMDLINE_KEY = "cmdline";

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "MSGService: onBind");
        Handler h = new Handler(Looper.getMainLooper(), this);
        Messenger m = new Messenger(h);
        return m.getBinder();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what != MSG_GETINFO)
            return false;
        Message reply = Message.obtain();
        reply.what = msg.what;
        reply.arg1 = Process.myPid();
        reply.arg2 = Process.myUid();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream("/proc/cmdline")) {
            // libsu internal util method, pumps input to output
            Utils.pump(in, out);
        } catch (IOException e) {
            Log.e(TAG, "IO error", e);
        }
        Bundle data = new Bundle();
        data.putString(CMDLINE_KEY, out.toString());
        reply.setData(data);
        try {
            msg.replyTo.send(reply);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote error", e);
        }
        return false;
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        Log.d(TAG, "MSGService: onUnbind, client process unbound");
        // Default returns false, which means NOT daemon mode
        return super.onUnbind(intent);
    }
}
