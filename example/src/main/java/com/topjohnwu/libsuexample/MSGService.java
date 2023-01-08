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

package com.topjohnwu.libsuexample;

import static com.topjohnwu.libsuexample.MainActivity.TAG;

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

import com.topjohnwu.superuser.ipc.RootService;

import java.util.UUID;

// Demonstrate root service using Messengers
class MSGService extends RootService implements Handler.Callback {

    static final int MSG_GETINFO = 1;
    static final int MSG_STOP = 2;
    static final String UUID_KEY = "uuid";

    private String uuid;

    @Override
    public void onCreate() {
        uuid = UUID.randomUUID().toString();
        Log.d(TAG, "MSGService: onCreate, " + uuid);
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "MSGService: onBind");
        Handler h = new Handler(Looper.getMainLooper(), this);
        Messenger m = new Messenger(h);
        return m.getBinder();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_STOP) {
            stopSelf();
            return false;
        }
        if (msg.what != MSG_GETINFO)
            return false;
        Message reply = Message.obtain();
        reply.what = msg.what;
        reply.arg1 = Process.myPid();
        reply.arg2 = Process.myUid();
        Bundle data = new Bundle();
        data.putString(UUID_KEY, uuid);
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
        // Default returns false, which means onRebind will not be called
        return false;
    }
}
