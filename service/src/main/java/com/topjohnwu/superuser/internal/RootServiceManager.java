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

import static com.topjohnwu.superuser.internal.RootServerMain.CMDLINE_START_SERVICE;
import static com.topjohnwu.superuser.internal.RootServerMain.CMDLINE_STOP_SERVICE;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager implements IBinder.DeathRecipient, Handler.Callback {

    private static RootServiceManager mInstance;

    static final String TAG = "IPC";
    static final String BUNDLE_DEBUG_KEY = "debug";
    static final String BUNDLE_BINDER_KEY = "binder";
    static final String LOGGING_ENV = "LIBSU_VERBOSE_LOGGING";

    static final int MSG_ACK = 1;
    static final int MSG_STOP = 2;

    private static final String INTENT_EXTRA_KEY = "extra.bundle";

    public static RootServiceManager getInstance() {
        if (mInstance == null) {
            mInstance = new RootServiceManager();
        }
        return mInstance;
    }

    @SuppressLint("WrongConstant")
    static Intent getBroadcastIntent(Context context, String action, IBinder binder) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, binder);
        return new Intent(action)
                .setPackage(context.getPackageName())
                .addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL)
                .putExtra(INTENT_EXTRA_KEY, bundle);
    }

    private static void enforceMainThread() {
        if (!ShellUtils.onMainThread()) {
            throw new IllegalStateException("This method can only be called on the main thread");
        }
    }

    @NonNull
    private static ComponentName enforceIntent(Intent intent) {
        ComponentName name = intent.getComponent();
        if (name == null) {
            throw new IllegalArgumentException("The intent does not have a component set");
        }
        if (!name.getPackageName().equals(Utils.getContext().getPackageName())) {
            throw new IllegalArgumentException("RootServices outside of the app are not supported");
        }
        return name;
    }

    private IRootServiceManager mRM;
    private List<Runnable> pendingTasks;
    private String filterAction;

    private final Map<ComponentName, RemoteService> services;
    private final Map<ServiceConnection, Pair<RemoteService, Executor>> connections;

    private RootServiceManager() {
        if (Build.VERSION.SDK_INT >= 19) {
            services = new ArrayMap<>();
            connections = new ArrayMap<>();
        } else {
            services = new HashMap<>();
            connections = new HashMap<>();
        }
    }

    private Runnable createStartRootProcessTask(ComponentName name, String action) {
        Context context = Utils.getContext();
        Bundle b = null;
        if (filterAction == null) {
            filterAction = UUID.randomUUID().toString();
            Bundle connectArgs = new Bundle();
            b = connectArgs;

            // Receive ACK and service stop signal
            Handler h = new Handler(Looper.getMainLooper(), this);
            Messenger m = new Messenger(h);
            connectArgs.putBinder(BUNDLE_BINDER_KEY, m.getBinder());

            // Register receiver to receive binder from root process
            IntentFilter filter = new IntentFilter(filterAction);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
                    if (bundle == null)
                        return;
                    IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
                    if (binder == null)
                        return;
                    IRootServiceManager m = IRootServiceManager.Stub.asInterface(binder);
                    try {
                        m.connect(connectArgs);
                    } catch (RemoteException e) {
                        Utils.err(TAG, e);
                    }
                }
            };
            context.registerReceiver(receiver, filter);
        }

        Context de = Utils.getDeContext(context);
        File mainJar = new File(de.getCacheDir(), "main.jar");

        String logParams = "";
        String debugParams = "";

        if (Utils.vLog()) {
            logParams = LOGGING_ENV + "=1";
        }

        // Only support debugging on SDK >= 27
        if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
            if (b != null) {
                b.putBoolean(BUNDLE_DEBUG_KEY, true);
            }
            // Reference of the params to start jdwp:
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            if (Build.VERSION.SDK_INT == 27) {
                debugParams = "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable";
            } else {
                debugParams = "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable";
            }
        }

        String cmd = String.format(Locale.ROOT,
                "(%s CLASSPATH=%s /proc/%d/exe %s /system/bin --nice-name=%s:root " +
                "com.topjohnwu.superuser.internal.RootServerMain %s %d %s %s)&",
                logParams, mainJar, Process.myPid(), debugParams, context.getPackageName(),
                name.flattenToString().replace("$", "\\$"), // args[0]
                Process.myUid(),                            // args[1]
                filterAction,                               // args[2]
                action);                                    // args[3]

        return () -> {
            try {
                // Dump main.jar as trampoline
                try (InputStream in = context.getResources().getAssets().open("main.jar");
                     OutputStream out = new FileOutputStream(mainJar)) {
                    Utils.pump(in, out);
                }
                Shell.su(cmd).exec();
            } catch (IOException e) {
                Utils.err(TAG, e);
            }
        };
    }

    private boolean bind(Intent intent, Executor executor, ServiceConnection conn) {
        enforceMainThread();

        // Local cache
        ComponentName name = enforceIntent(intent);
        RemoteService s = services.get(name);
        if (s != null) {
            connections.put(conn, new Pair<>(s, executor));
            s.refCount++;
            executor.execute(() -> conn.onServiceConnected(name, s.binder));
            return true;
        }

        if (mRM == null)
            return false;

        try {
            IBinder binder = mRM.bind(intent);
            if (binder != null) {
                RemoteService r = new RemoteService(name, binder);
                connections.put(conn, new Pair<>(r, executor));
                services.put(name, r);
                executor.execute(() -> conn.onServiceConnected(name, binder));
            } else if (Build.VERSION.SDK_INT >= 28) {
                executor.execute(() -> conn.onNullBinding(name));
            }
        } catch (RemoteException e) {
            Utils.err(TAG, e);
            mRM = null;
            return false;
        }

        return true;
    }

    public Runnable createBindTask(Intent intent, Executor executor, ServiceConnection conn) {
        if (!bind(intent, executor, conn)) {
            boolean launch = false;
            if (pendingTasks == null) {
                pendingTasks = new ArrayList<>();
                launch = true;
            }
            pendingTasks.add(() -> bind(intent, executor, conn));
            if (launch) {
                return createStartRootProcessTask(intent.getComponent(), CMDLINE_START_SERVICE);
            }
        }
        return null;
    }

    public void unbind(@NonNull ServiceConnection conn) {
        enforceMainThread();

        if (mRM == null)
            return;

        Pair<RemoteService, Executor> p = connections.remove(conn);
        if (p != null) {
            p.first.refCount--;
            p.second.execute(() -> conn.onServiceDisconnected(p.first.name));
            if (p.first.refCount == 0) {
                // Actually close the service
                services.remove(p.first.name);
                try {
                    mRM.unbind(p.first.name);
                } catch (RemoteException e) {
                    Utils.err(TAG, e);
                }
            }
        }
    }

    private boolean stopInternal(ComponentName name) {
        RemoteService s = services.remove(name);
        if (s == null)
            return false;

        // Notify all connections
        Iterator<Map.Entry<ServiceConnection, Pair<RemoteService, Executor>>> it =
                connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServiceConnection, Pair<RemoteService, Executor>> e = it.next();
            if (e.getValue().first.equals(s)) {
                e.getValue().second.execute(() -> e.getKey().onServiceDisconnected(name));
                it.remove();
            }
        }
        return true;
    }

    public void stop(Intent intent) {
        enforceMainThread();

        ComponentName name = enforceIntent(intent);
        if (mRM == null) {
            // Start a new root process
            Runnable r = createStartRootProcessTask(name, CMDLINE_STOP_SERVICE);
            Shell.EXECUTOR.execute(r);
            return;
        }

        if (!stopInternal(name))
            return;
        try {
            mRM.stop(name);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
        }
    }

    @Override
    public void binderDied() {
        UiThreadHandler.run(() -> {
            if (mRM != null) {
                mRM.asBinder().unlinkToDeath(this, 0);
                mRM = null;
            }

            // Notify all connections
            for (Map.Entry<ServiceConnection, Pair<RemoteService, Executor>> e
                    : connections.entrySet()) {
                e.getValue().second.execute(() ->
                        e.getKey().onServiceDisconnected(e.getValue().first.name));
            }
            connections.clear();
            services.clear();
        });
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case MSG_ACK:
                IBinder b = ((Bundle) msg.obj).getBinder(BUNDLE_BINDER_KEY);
                if (b == null)
                    return false;
                try {
                    b.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    return false;
                }
                mRM = IRootServiceManager.Stub.asInterface(b);
                List<Runnable> tasks = pendingTasks;
                pendingTasks = null;
                if (tasks != null) {
                    for (Runnable r : tasks) {
                        r.run();
                    }
                }
                break;
            case MSG_STOP:
                stopInternal((ComponentName) msg.obj);
                break;
        }
        return false;
    }

    static class RemoteService {
        final ComponentName name;
        final IBinder binder;
        int refCount = 1;

        RemoteService(ComponentName name, IBinder binder) {
            this.name = name;
            this.binder = binder;
        }
    }
}
