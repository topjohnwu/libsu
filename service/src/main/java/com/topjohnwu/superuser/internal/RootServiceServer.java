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

package com.topjohnwu.superuser.internal;

import static com.topjohnwu.superuser.internal.RootServerMain.attachBaseContext;
import static com.topjohnwu.superuser.internal.RootServerMain.getServiceName;
import static com.topjohnwu.superuser.internal.RootServiceManager.ACTION_ENV;
import static com.topjohnwu.superuser.internal.RootServiceManager.BUNDLE_BINDER_KEY;
import static com.topjohnwu.superuser.internal.RootServiceManager.BUNDLE_DEBUG_KEY;
import static com.topjohnwu.superuser.internal.RootServiceManager.LOGGING_ENV;
import static com.topjohnwu.superuser.internal.RootServiceManager.MSG_ACK;
import static com.topjohnwu.superuser.internal.RootServiceManager.MSG_STOP;
import static com.topjohnwu.superuser.internal.RootServiceManager.TAG;
import static com.topjohnwu.superuser.internal.Utils.context;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceServer extends IRootServiceManager.Stub implements IBinder.DeathRecipient {

    private static RootServiceServer mInstance;

    public static RootServiceServer getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RootServiceServer(context);
        }
        return mInstance;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final FileObserver observer;  /* A strong reference is required */
    private final Map<ComponentName, ServiceContainer> activeServices;
    private Messenger client;
    private boolean isDaemon = false;
    private String mAction;

    private RootServiceServer(Context context) {
        Shell.enableVerboseLogging = System.getenv(LOGGING_ENV) != null;
        mAction = System.getenv(ACTION_ENV);
        Utils.context = context;
        if (Build.VERSION.SDK_INT >= 19) {
            activeServices = new ArrayMap<>();
        } else {
            activeServices = new HashMap<>();
        }
        observer = new AppObserver(new File(context.getPackageCodePath()));

        broadcast();
        observer.startWatching();
    }

    @Override
    public void connect(Bundle bundle) {
        if (client != null)
            return;

        IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
        if (binder == null)
            return;
        final Messenger c;
        try {
            binder.linkToDeath(this, 0);
            c = new Messenger(binder);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
            return;
        }

        if (bundle.getBoolean(BUNDLE_DEBUG_KEY, false)) {
            // ActivityThread.attach(true, 0) will set this to system_process
            HiddenAPIs.setAppName(context.getPackageName() + ":root");
            Utils.log(TAG, "Waiting for debugger to be attached...");
            // For some reason Debug.waitForDebugger() won't work, manual spin lock...
            while (!Debug.isDebuggerConnected()) {
                try { Thread.sleep(200); }
                catch (InterruptedException ignored) {}
            }
            Utils.log(TAG, "Debugger attached!");
        }

        Message m = Message.obtain();
        m.what = MSG_ACK;
        try {
            c.send(m);
            client = c;
        } catch (RemoteException ignored) {}
    }

    @Override
    public void broadcast() {
        Intent intent = RootServiceManager.getBroadcastIntent(context, mAction, this);
        context.sendBroadcast(intent);
    }

    @Override
    public IBinder bind(Intent intent) {
        IBinder[] b = new IBinder[1];
        UiThreadHandler.runAndWait(() -> {
            try {
                b[0] = bindInternal(intent);
            } catch (Exception e) {
                Utils.err(TAG, e);
            }
        });
        return b[0];
    }

    @Override
    public void unbind(ComponentName name) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " unbind");
            stopService(name, false);
        });
    }

    @Override
    public void stop(ComponentName name) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " stop");
            stopService(name, true);
            // If no client is connected yet, broadcast anyways
            if (client == null) {
                broadcast();
            }
        });
    }

    @Override
    public void setAction(String action) {
        mAction = action;
    }

    @Override
    public void binderDied() {
        Messenger c = client;
        client = null;
        if (c != null)
            c.getBinder().unlinkToDeath(this, 0);
        UiThreadHandler.run(() -> {
            Utils.log(TAG, "Client process terminated");
            stopAllService(false);
        });
    }

    public void selfStop(ComponentName name) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " selfStop");
            stopService(name, true);
            Messenger c = client;
            if (c != null) {
                Message m = Message.obtain();
                m.what = MSG_STOP;
                m.obj = name;
                try {
                    c.send(m);
                } catch (RemoteException e) {
                    Utils.err(TAG, e);
                }
            }
        });
    }

    public void register(RootService service) {
        ServiceContainer c = new ServiceContainer();
        c.service = service;
        activeServices.put(service.getComponentName(), c);
    }

    private IBinder bindInternal(Intent intent) throws Exception {
        ComponentName name = intent.getComponent();

        ServiceContainer c = activeServices.get(name);
        if (c == null) {
            Class<?> clz = Class.forName(name.getClassName());
            Constructor<?> ctor = clz.getDeclaredConstructor();
            ctor.setAccessible(true);
            attachBaseContext.invoke(ctor.newInstance(), context);

            // RootService should be registered after attachBaseContext
            c = activeServices.get(name);
            if (c == null) {
                return null;
            }
        }

        if (c.binder != null) {
            Utils.log(TAG, name.getClassName() + " rebind");
            c.service.onRebind(c.intent);
        } else {
            Utils.log(TAG, name.getClassName() + " bind");
            c.binder = c.service.onBind(intent);
            c.intent = intent.cloneFilter();
        }

        return c.binder;
    }

    private void setAsDaemon() {
        if (!isDaemon) {
            // Register ourselves as system service
            HiddenAPIs.addService(getServiceName(context.getPackageName()), this);
            isDaemon = true;
        }
    }

    private void stopService(ComponentName className, boolean force) {
        ServiceContainer c = activeServices.get(className);
        if (c != null) {
            if (!c.service.onUnbind(c.intent) || force) {
                c.service.onDestroy();
                activeServices.remove(className);
            } else {
                setAsDaemon();
            }
        }
        if (activeServices.isEmpty()) {
            // Terminate root process
            System.exit(0);
        }
    }

    private void stopAllService(boolean force) {
        Iterator<Map.Entry<ComponentName, ServiceContainer>> it =
                activeServices.entrySet().iterator();
        while (it.hasNext()) {
            ServiceContainer c = it.next().getValue();
            if (!c.service.onUnbind(c.intent) || force) {
                c.service.onDestroy();
                it.remove();
            } else {
                setAsDaemon();
            }
        }
        if (force || activeServices.isEmpty()) {
            // Terminate root process
            System.exit(0);
        }
    }

    class AppObserver extends FileObserver {

        private final String name;

        AppObserver(File path) {
            super(path.getParent(), CREATE|DELETE|DELETE_SELF|MOVED_TO|MOVED_FROM);
            Utils.log(TAG, "Start monitoring: " + path.getParent());
            name = path.getName();
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            // App APK update, force close the root process
            if (event == DELETE_SELF || name.equals(path)) {
                UiThreadHandler.run(() -> {
                    Utils.log(TAG, "App updated, terminate");
                    stopAllService(true);
                });
            }
        }
    }

    static class ServiceContainer {
        RootService service;
        Intent intent;
        IBinder binder;
    }
}
