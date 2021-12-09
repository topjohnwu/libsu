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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager implements IBinder.DeathRecipient, Handler.Callback {

    private static RootServiceManager mInstance;

    static final String TAG = "IPC";
    static final String BUNDLE_DEBUG_KEY = "debug";
    static final String BUNDLE_BINDER_KEY = "binder";
    static final String LOGGING_ENV = "LIBSU_VERBOSE_LOGGING";

    static final int MSG_ACK = 1;
    static final int MSG_STOP = 2;

    private static final String MAIN_CLASSNAME = "com.topjohnwu.superuser.internal.RootServerMain";
    private static final String INTENT_EXTRA_KEY = "binder_bundle";
    private static final String ACTION_ENV = "LIBSU_BROADCAST_ACTION";

    public static RootServiceManager getInstance() {
        if (mInstance == null) {
            mInstance = new RootServiceManager();
        }
        return mInstance;
    }

    @SuppressLint("WrongConstant")
    static Intent getBroadcastIntent(Context context, IBinder binder) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, binder);
        return new Intent()
                .setPackage(context.getPackageName())
                .setAction(System.getenv(ACTION_ENV))
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
            throw new IllegalArgumentException("RootServices outside of the app are not supported");
        }
        return name;
    }

    private IRootServiceManager manager;
    private IBinder mRemote;
    private List<Runnable> pendingTasks;
    private String mAction;

    private final ExecutorService serialExecutor = new SerialExecutorService();
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

    private void startRootProcess(Context context, String args) {
        // Dump main.jar as trampoline
        final File mainJar;
        try {
            mainJar = dumpMainJar(context);
        } catch (IOException e) {
            return;
        }

        Bundle b = null;
        if (mAction == null) {
            mAction = UUID.randomUUID().toString();
            Bundle connectArgs = new Bundle();
            b = connectArgs;

            // Receive ACK and service stop signal
            Handler h = new Handler(Looper.getMainLooper(), this);
            Messenger m = new Messenger(h);
            connectArgs.putBinder(BUNDLE_BINDER_KEY, m.getBinder());

            // Register receiver to receive binder from root process
            IntentFilter filter = new IntentFilter(mAction);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // Receive new binder, treat as if the previous one died
                    binderDied();

                    Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
                    if (bundle == null)
                        return;
                    IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
                    if (binder == null)
                        return;
                    IRootServiceManager m = IRootServiceManager.Stub.asInterface(binder);
                    try {
                        binder.linkToDeath(RootServiceManager.this, 0);
                        m.connect(connectArgs);
                        mRemote = binder;
                    } catch (RemoteException e) {
                        Utils.err(TAG, e);
                    }
                }
            };
            context.registerReceiver(receiver, filter);
        }

        StringBuilder sb = new StringBuilder();
        sb.append('(');

        if (Utils.vLog()) {
            sb.append(LOGGING_ENV);
            sb.append("=1 ");
        }

        sb.append(ACTION_ENV);
        sb.append('=');
        sb.append(mAction);
        sb.append(" CLASSPATH=");
        sb.append(mainJar);
        sb.append(" /proc/");
        sb.append(Process.myPid());
        sb.append("/exe");

        // Only support debugging on SDK >= 27
        if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
            if (b != null) {
                b.putBoolean(BUNDLE_DEBUG_KEY, true);
            }
            // Reference of the params to start jdwp:
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            if (Build.VERSION.SDK_INT == 27) {
                sb.append(" -Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable");
            } else {
                sb.append(" -XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable");
            }
        }

        sb.append(" /system/bin ");
        sb.append(args);
        sb.append(")&");

        Shell.su(sb.toString()).exec();
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
            pendingTasks.add(() -> bind(intent, executor, conn));
            if (launch) {
                serialExecutor.execute(() -> {
                    Context context = Utils.getContext();
                    String args = String.format("--nice-name=%s:root %s %s %s",
                            context.getPackageName(), MAIN_CLASSNAME,
                            name.flattenToString().replace("$", "\\$"), CMDLINE_START_SERVICE);
                    startRootProcess(context, args);
                });
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
                    manager.unbind(p.first.name);
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
            serialExecutor.execute(() -> {
                String args = String.format("%s %s %s", MAIN_CLASSNAME,
                        name.flattenToString().replace("$", "\\$"), CMDLINE_STOP_SERVICE);
                startRootProcess(Utils.getContext(), args);
            });
            return;
        }

        if (!stopInternal(name))
            return;
        try {
            manager.stop(name);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
        }
    }

    @Override
    public void binderDied() {
        UiThreadHandler.run(() -> {
            if (mRemote != null) {
                mRemote.unlinkToDeath(this, 0);
                mRemote = null;
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
        switch (msg.what) {
            case MSG_ACK:
                manager = IRootServiceManager.Stub.asInterface(mRemote);
                List<Runnable> tasks = pendingTasks;
                pendingTasks = null;
                for (Runnable r : tasks) {
                    r.run();
                }
                break;
            case MSG_STOP:
                ComponentName name = (ComponentName) msg.obj;
                stopInternal(name);
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
