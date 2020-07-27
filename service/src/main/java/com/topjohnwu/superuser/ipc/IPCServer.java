/*
 * Copyright 2020 John "topjohnwu" Wu
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

package com.topjohnwu.superuser.ipc;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.IBinder;
import android.os.Looper;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IRootIPC;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.internal.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.topjohnwu.superuser.ipc.IPCClient.INTENT_DEBUG_KEY;
import static com.topjohnwu.superuser.ipc.IPCClient.INTENT_LOGGING_KEY;

class IPCServer extends IRootIPC.Stub implements IBinder.DeathRecipient {

    private final ComponentName mName;
    private final RootService service;

    private IBinder mClient;
    private Intent mIntent;

    // Set this flag to silence AMS's complaints
    @SuppressWarnings("JavaReflectionMemberAccess")
    private static int FLAG_RECEIVER_FROM_SHELL() {
        try {
            Field f = Intent.class.getDeclaredField("FLAG_RECEIVER_FROM_SHELL");
            return (int) f.get(null);
        } catch (Exception e) {
            // Only exist on Android 8.0+
            return 0;
        }
    }

    /**
     * These private APIs are very unlikely to change, should be
     * stable across different Android versions and OEMs.
     */
    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    private static void setAppName(String name) {
        try {
            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            Method m = ddm.getDeclaredMethod("setAppName", String.class, int.class);
            m.invoke(null, name, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    IPCServer(Context context, ComponentName name) throws Exception {
        mName = name;

        Class<RootService> clz = (Class<RootService>) Class.forName(name.getClassName());
        Constructor<RootService> constructor = clz.getDeclaredConstructor();
        constructor.setAccessible(true);
        service = constructor.newInstance();
        service.attach(context);
        service.onCreate();

        Intent broadcast = IPCClient.getBroadcastIntent(name, this);
        broadcast.addFlags(FLAG_RECEIVER_FROM_SHELL());
        context.sendBroadcast(broadcast);

        Looper.loop();
    }

    @Override
    public synchronized IBinder bind(Intent intent, IBinder client) {
        // ComponentName doesn't match, abort
        if (!mName.equals(intent.getComponent()))
            System.exit(1);

        Shell.Config.verboseLogging(intent.getBooleanExtra(INTENT_LOGGING_KEY, false));
        if (intent.getBooleanExtra(INTENT_DEBUG_KEY, false)) {
            // ActivityThread.attach(true, 0) will set this to system_process
            setAppName(service.getPackageName() + ":root");
            // For some reason Debug.waitForDebugger() won't work, manual spin lock...
            while (!Debug.isDebuggerConnected()) {
                try { Thread.sleep(200); }
                catch (InterruptedException ignored) {}
            }
        }

        try {
            mClient = client;
            client.linkToDeath(this, 0);

            class Container { IBinder obj; }
            Container binderContainer = new Container();
            UiThreadHandler.runAndWait(() -> {
                if (mIntent == null)
                    service.onRebind(intent);
                binderContainer.obj = service.onBind(intent);
            });
            mIntent = intent.cloneFilter();
            return binderContainer.obj;
        } catch (Exception e) {
            Utils.err(e);
            return null;
        }
    }

    @Override
    public synchronized void unbind() {
        mClient.unlinkToDeath(this, 0);
        mClient = null;
        UiThreadHandler.run(() -> {
            if (!service.onUnbind(mIntent)) {
                service.onDestroy();
                System.exit(0);
            }
        });
    }

    @Override
    public void binderDied() {
        unbind();
    }
}
