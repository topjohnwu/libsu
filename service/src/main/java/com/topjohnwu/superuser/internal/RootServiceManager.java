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
import java.util.concurrent.Executor;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager implements IBinder.DeathRecipient, Handler.Callback {

    private static RootServiceManager mInstance;

    static final String TAG = "IPC";
    static final String BUNDLE_DEBUG_KEY = "debug";
    static final String BUNDLE_BINDER_KEY = "binder";
    static final String LOGGING_ENV = "LIBSU_VERBOSE_LOGGING";

    private static final String BROADCAST_ACTION = "com.topjohnwu.superuser.BROADCAST_IPC";
    private static final String MAIN_CLASSNAME = "com.topjohnwu.superuser.internal.RootServerMain";
    private static final String INTENT_EXTRA_KEY = "binder_bundle";

    public static RootServiceManager getInstance() {
        if (mInstance == null) {
            mInstance = new RootServiceManager();
        }
        return mInstance;
    }

    private static String getBroadcastAction(Context context) {
        return BROADCAST_ACTION + "/" + context.getPackageName();
    }

    @SuppressLint("WrongConstant")
    static Intent getBroadcastIntent(Context context, IBinder binder) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, binder);
        return new Intent()
                .setPackage(context.getPackageName())
                .setAction(getBroadcastAction(context))
                .addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL)
                .putExtra(INTENT_EXTRA_KEY, bundle);
    }

    private static File dumpMainJar(Context context) throws IOException {
        Context de = Utils.getDeContext(context);
        File mainJar = new File(de.getCacheDir(), "main.jar");
        try (InputStream in = context.getResources().getAssets().open("main.jar");
             OutputStream out = new FileOutputStream(mainJar)) {
            Utils.pump(in, out);
        }
        return mainJar;
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
            throw new IllegalArgumentException("RootService shall be in the app's package");
        }
        return name;
    }

    private IRootServiceManager manager;
    private List<BindRequest> pendingTasks;

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

    private void startRootProcess(Context context, ComponentName name) {
        Bundle connectArgs = new Bundle();

        // Receive RSM service stop signal
        Handler h = new Handler(Looper.getMainLooper(), this);
        Messenger m = new Messenger(h);
        connectArgs.putBinder(BUNDLE_BINDER_KEY, m.getBinder());

        String debugParams = "";
        // Only support debugging on SDK > 27
        if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
            connectArgs.putBoolean(BUNDLE_DEBUG_KEY, true);

            // Also debug the remote root server
            // Reference of the params to start jdwp:
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            switch (Build.VERSION.SDK_INT) {
                case 27:
                    debugParams = "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable";
                    break;
                case 28:
                    debugParams = "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable";
                    break;
                default:
                    debugParams = "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y";
                    break;
            }
        }

        // Dump main.jar as trampoline
        final File mainJar;
        try {
            mainJar = dumpMainJar(context);
        } catch (IOException e) {
            return;
        }

        // Execute main.jar through root shell
        String cmd = String.format(Locale.US,
                "CLASSPATH=%s /proc/%d/exe %s /system/bin --nice-name=%s:root %s %s %s",
                mainJar, Process.myPid(), debugParams, context.getPackageName(),
                MAIN_CLASSNAME, name.flattenToString(), CMDLINE_START_SERVICE);
        // Make sure cmd is properly formatted in shell
        cmd = cmd.replace("$", "\\$");
        if (Utils.vLog())
            cmd = LOGGING_ENV + "=1 " + cmd;

        // Register receiver to receive binder from root process
        IntentFilter filter = new IntentFilter(getBroadcastAction(context));
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
                IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
                IRootServiceManager m = IRootServiceManager.Stub.asInterface(binder);
                try {
                    m.connect(connectArgs);
                    binder.linkToDeath(RootServiceManager.this, 0);
                } catch (RemoteException e) {
                    Utils.err(TAG, e);
                    return;
                }
                manager = m;
                List<BindRequest> requests = pendingTasks;
                pendingTasks = null;
                for (BindRequest r : requests) {
                    bind(r.intent, r.executor, r.conn);
                }
            }
        };
        context.registerReceiver(receiver, filter);

        Shell.su("(" + cmd + ")&").exec();
    }

    public void bind(Intent intent, Executor executor, ServiceConnection conn) {
        enforceMainThread();

        // Local cache
        ComponentName name = enforceIntent(intent);
        RemoteService s = services.get(name);
        if (s != null) {
            connections.put(conn, new Pair<>(s, executor));
            s.refCount++;
            executor.execute(() -> conn.onServiceConnected(name, s.binder));
            return;
        }

        if (manager == null) {
            boolean launch = false;
            if (pendingTasks == null) {
                pendingTasks = new ArrayList<>();
                launch = true;
            }
            pendingTasks.add(new BindRequest(intent, executor, conn));
            if (launch) {
                Shell.EXECUTOR.execute(() -> startRootProcess(Utils.getContext(), name));
            }
        } else {
            try {
                IBinder binder = manager.bind(intent);
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
            }
        }
    }

    public void unbind(@NonNull ServiceConnection conn) {
        enforceMainThread();

        if (manager == null)
            return;

        Pair<RemoteService, Executor> p = connections.remove(conn);
        if (p != null) {
            p.first.refCount--;
            p.second.execute(() -> conn.onServiceDisconnected(p.first.name));
            if (p.first.refCount == 0) {
                // Actually close the service
                services.remove(p.first.name);
                try {
                    manager.unbind(p.first.name.getClassName());
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
        if (manager == null) {
            // Start a new root process

            // Dump main.jar as trampoline
            final File mainJar;
            try {
                mainJar = dumpMainJar(Utils.getContext());
            } catch (IOException e) {
                return;
            }

            // Execute main.jar through root shell
            String cmd = String.format(Locale.US,
                    "CLASSPATH=%s /proc/%d/exe /system/bin %s %s %s",
                    mainJar, Process.myPid(), MAIN_CLASSNAME,
                    name.flattenToString(), CMDLINE_STOP_SERVICE);
            // Make sure cmd is properly formatted in shell
            cmd = cmd.replace("$", "\\$");

            Shell.su("(" + cmd + ")&").exec();
            return;
        }

        if (!stopInternal(name))
            return;
        try {
            manager.stop(name.getClassName());
        } catch (RemoteException e) {
            Utils.err(TAG, e);
        }
    }

    @Override
    public void binderDied() {
        UiThreadHandler.run(() -> {
            if (manager != null) {
                manager.asBinder().unlinkToDeath(this, 0);
                manager = null;
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
        ComponentName name = (ComponentName) msg.obj;
        stopInternal(name);
        return false;
    }

    static class BindRequest {
        final Intent intent;
        final Executor executor;
        final ServiceConnection conn;

        BindRequest(Intent intent, Executor executor, ServiceConnection conn) {
            this.intent = intent;
            this.executor = executor;
            this.conn = conn;
        }
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
