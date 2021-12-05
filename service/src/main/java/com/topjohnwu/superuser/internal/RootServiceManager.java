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

import static com.topjohnwu.superuser.internal.IPCMain.STOP_SERVICE_TRANSACTION;
import static com.topjohnwu.superuser.internal.RootServiceClient.BUNDLE_BINDER_KEY;
import static com.topjohnwu.superuser.internal.RootServiceClient.BUNDLE_DEBUG_KEY;
import static com.topjohnwu.superuser.internal.RootServiceClient.LOGGING_ENV;
import static com.topjohnwu.superuser.internal.RootServiceClient.TAG;
import static com.topjohnwu.superuser.internal.Utils.context;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager extends IRootServiceManager.Stub implements IBinder.DeathRecipient {

    @SuppressLint("StaticFieldLeak")
    private static RootServiceManager mInstance;

    private final AtomicReference<IBinder> client;

    @SuppressWarnings("FieldCanBeLocal")
    private final FileObserver observer;  /* A strong reference is required */

    private final Map<String, ServiceContainer> activeServices;
    private boolean isDaemon = false;

    private RootServiceManager(Context context) {
        IBinder binder = HiddenAPIs.getService(context.getPackageName());
        if (binder != null) {
            // There was already a root process running
            IRootServiceManager mgr = IRootServiceManager.Stub.asInterface(binder);
            try {
                // Trigger re-broadcast
                mgr.broadcast();

                // Our work is done!
                System.exit(0);
            } catch (RemoteException e) {
                // Daemon dead, continue
            }
        }

        Shell.enableVerboseLogging = System.getenv(LOGGING_ENV) != null;
        Utils.context = context;
        if (Build.VERSION.SDK_INT >= 19) {
            activeServices = new ArrayMap<>();
        } else {
            activeServices = new HashMap<>();
        }
        client = new AtomicReference<>();
        observer = new AppObserver(new File(context.getPackageCodePath()));

        broadcast();
        observer.startWatching();
    }

    public static RootServiceManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RootServiceManager(context);
        }
        return mInstance;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == STOP_SERVICE_TRANSACTION) {
            String className = data.readString();
            stop(className);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    @Override
    public void connect(Bundle bundle) {
        if (client.get() != null)
            return;

        IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
        if (binder == null)
            return;
        try {
            binder.linkToDeath(this, 0);
            client.set(binder);
        } catch (RemoteException ignored) {}

        if (bundle.getBoolean(BUNDLE_DEBUG_KEY, false)) {
            // ActivityThread.attach(true, 0) will set this to system_process
            HiddenAPIs.setAppName(context.getPackageName() + ":root");
            // For some reason Debug.waitForDebugger() won't work, manual spin lock...
            while (!Debug.isDebuggerConnected()) {
                try { Thread.sleep(200); }
                catch (InterruptedException ignored) {}
            }
        }
    }

    @Override
    public void broadcast() {
        Intent intent = RootServiceClient.getBroadcastIntent(context, this);
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
    public void unbind(String className) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, className + " unbind");
            stopService(className, false);
        });

    }

    @Override
    public void stop(String className) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, className + " stop");
            stopService(className, true);
        });
    }

    @Override
    public void binderDied() {
        IBinder binder = client.getAndSet(null);
        if (binder != null) {
            binder.unlinkToDeath(this, 0);
        }
        UiThreadHandler.run(() -> {
            Utils.log(TAG, "Client process terminated");
            stopAllService(false);
        });
    }

    public void register(RootService svc) {
        final String className = svc.getClass().getName();
        ServiceContainer c = activeServices.get(className);
        if (c == null) {
            c = new ServiceContainer();
            activeServices.put(className, c);
            c.service = svc;
        }
    }

    private IBinder bindInternal(Intent intent) throws Exception {
        final String className = intent.getComponent().getClassName();

        ServiceContainer c = activeServices.get(className);
        if (c == null) {
            c = new ServiceContainer();
            activeServices.put(className, c);
            Class<?> clz = Class.forName(className);
            Constructor<?> ctor = clz.getDeclaredConstructor();
            ctor.setAccessible(true);
            c.service = (RootService) ctor.newInstance();
            Method m = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            m.setAccessible(true);
            m.invoke(c.service, context);
        }

        if (c.binder != null) {
            Utils.log(TAG, className + " rebind");
            c.service.onRebind(c.intent);
        } else {
            Utils.log(TAG, className + " bind");
            c.binder = c.service.onBind(intent);
            c.intent = intent.cloneFilter();
        }

        return c.binder;
    }

    private void setAsDaemon() {
        if (!isDaemon) {
            // Register ourselves as system service
            HiddenAPIs.addService(context.getPackageName(), this);
            isDaemon = true;
        }
    }

    private void stopService(String className, boolean force) {
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
        Iterator<Map.Entry<String, ServiceContainer>> it = activeServices.entrySet().iterator();
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
