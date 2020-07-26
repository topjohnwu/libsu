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

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IRootIPC;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.internal.Utils;

import java.lang.reflect.Constructor;

import static com.topjohnwu.superuser.ipc.RootService.INTENT_VERBOSE_KEY;

class IPCServer extends IRootIPC.Stub implements IBinder.DeathRecipient {

    private RootService service;
    private IBinder mClient;
    private Intent mIntent;
    private Context mContext;

    IPCServer(Context context) {
        mContext = context;
        String packageName = context.getPackageName();
        Intent broadcast = IPCClient.getBroadcastIntent(packageName, this);
        context.sendBroadcast(broadcast);
        Looper.loop();
    }

    static class Container<T> {
        T obj;
    }

    @Override
    public synchronized IBinder bind(Intent intent, IBinder client) {
        mIntent = intent.cloneFilter();
        Shell.Config.verboseLogging(intent.getBooleanExtra(INTENT_VERBOSE_KEY, false));
        boolean newService = false;
        try {
            if (service == null) {
                String name = intent.getComponent().getClassName();
                Class<? extends RootService> clz = (Class<? extends RootService>) Class.forName(name);
                Constructor<? extends RootService> constructor = clz.getDeclaredConstructor();
                constructor.setAccessible(true);
                service = constructor.newInstance();
                newService = true;
            }
            mClient = client;
            client.linkToDeath(this, 0);

            boolean finalNewService = newService;
            Container<IBinder> binderContainer = new Container<>();
            UiThreadHandler.runAndWait(() -> {
                if (finalNewService) {
                    service.attach(mContext);
                    service.onCreate();
                } else {
                    service.onRebind(intent);
                }
                binderContainer.obj = service.onBind(intent);
            });
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
            boolean rebind = service.onUnbind(mIntent);
            if (!rebind) {
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
