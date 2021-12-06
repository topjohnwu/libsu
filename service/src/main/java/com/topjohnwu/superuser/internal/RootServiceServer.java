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
import static com.topjohnwu.superuser.internal.RootServiceManager.BUNDLE_BINDER_KEY;
import static com.topjohnwu.superuser.internal.RootServiceManager.BUNDLE_DEBUG_KEY;
import static com.topjohnwu.superuser.internal.RootServiceManager.LOGGING_ENV;
import static com.topjohnwu.superuser.internal.RootServiceManager.TAG;
import static com.topjohnwu.superuser.internal.Utils.context;

import android.annotation.SuppressLint;
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

    @SuppressLint("StaticFieldLeak")
    private static RootServiceServer mInstance;

    public static RootServiceServer getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RootServiceServer(context);
        }
        return mInstance;
    }

    private Messenger client;

    @SuppressWarnings("FieldCanBeLocal")
    private final FileObserver observer;  /* A strong reference is required */

    private final Map<String, ServiceContainer> activeServices;
    private boolean isDaemon = false;

    private RootServiceServer(Context context) {
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
        try {
            binder.linkToDeath(this, 0);
            client = new Messenger(binder);
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
        Intent intent = RootServiceManager.getBroadcastIntent(context, this);
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

    public void selfStop(ComponentName name) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " selfStop");
            stopService(name.getClassName(), true);
            Messenger c = client;
            if (c != null) {
                Message m = Message.obtain();
                m.obj = name;
                try {
                    c.send(m);
                } catch (RemoteException e) {
                    Utils.err(TAG, e);
                }
            }
        });
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
            attachBaseContext.invoke(c.service, context);
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
            HiddenAPIs.addService(getServiceName(context.getPackageName()), this);
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
