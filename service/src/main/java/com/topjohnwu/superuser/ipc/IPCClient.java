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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.IRootIPC;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.topjohnwu.superuser.ipc.RootService.serialExecutor;

class IPCClient implements IBinder.DeathRecipient {
    private static final String BROADCAST_ACTION = "com.topjohnwu.superuser.BROADCAST_IPC";
    private static final String INTENT_EXTRA_KEY = "binder_bundle";
    private static final String BUNDLE_BINDER_KEY = "binder";

    private ComponentName name;
    private IRootIPC server = null;
    private IBinder binder = null;
    private Map<ServiceConnection, Executor> connections = new HashMap<>();

    IPCClient(Intent intent) throws InterruptedException, RemoteException, IOException {
        name = intent.getComponent();
        startRootServer(InternalUtils.getContext(), intent);
    }

    private void startRootServer(Context context, Intent intent)
            throws IOException, InterruptedException, RemoteException {
        // Dump main.jar as trampoline
        Context de = Build.VERSION.SDK_INT >= 24 ? context.createDeviceProtectedStorageContext() : context;
        File mainJar = new File(de.getCacheDir(), "main.jar");

        try (InputStream in = context.getResources().openRawResource(R.raw.main);
             OutputStream out = new FileOutputStream(mainJar)) {
            InternalUtils.pump(in, out);
        }

        // Register BinderReceiver to receive binder from root process
        context.registerReceiver(new BinderReceiver(), new IntentFilter(BROADCAST_ACTION));

        // Execute main.jar through root shell
        String app_process = new File("/proc/self/exe").getCanonicalPath();
        String cmd = String.format(
                "(CLASSPATH=%1$s %2$s /system/bin --nice-name=%4$s:root %3$s %4$s %5$s)&",
                mainJar, app_process, "com.topjohnwu.superuser.internal.IPCMain",
                context.getPackageName(), IPCServer.class.getName());
        synchronized (this) {
            Shell.su(cmd).exec();
            // Wait for broadcast receiver
            wait();
        }
        server.asBinder().linkToDeath(this, 0);
        binder = server.bind(intent, new Binder());
    }

    boolean isSameService(Intent intent) {
        return name.equals(intent.getComponent());
    }

    void newConnection(ServiceConnection conn, Executor executor) {
        connections.put(conn, executor);
        if (binder != null)
            executor.execute(() -> conn.onServiceConnected(name, binder));
        else if (Build.VERSION.SDK_INT >= 28)
            executor.execute(() -> conn.onNullBinding(name));
    }

    boolean unbind(ServiceConnection conn) {
        Executor executor = connections.remove(conn);
        if (executor != null) {
            executor.execute(() -> conn.onServiceDisconnected(name));
            if (connections.isEmpty()) {
                server.asBinder().unlinkToDeath(this, 0);
                try {
                    server.unbind();
                } catch (RemoteException ignored) {}
                server = null;
                binder = null;
                return true;
            }
        }
        return false;
    }

    @Override
    public void binderDied() {
        server.asBinder().unlinkToDeath(this, 0);
        server = null;
        binder = null;
        for (Map.Entry<ServiceConnection, Executor> entry : connections.entrySet()) {
            ServiceConnection conn = entry.getKey();
            entry.getValue().execute(() -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    conn.onBindingDied(name);
                }
                conn.onServiceDisconnected(name);
            });
        }
        connections.clear();
        serialExecutor.execute(() -> RootService.active.remove(this));
    }

    static Intent getBroadcastIntent(String packageName, IRootIPC.Stub ipc) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, ipc);
        return new Intent()
                .setPackage(packageName)
                .setAction(BROADCAST_ACTION)
                .putExtra(INTENT_EXTRA_KEY, bundle);
    }

    class BinderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.unregisterReceiver(this);
            Bundle bundle = intent.getBundleExtra(INTENT_EXTRA_KEY);
            IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
            synchronized (IPCClient.this) {
                server = IRootIPC.Stub.asInterface(binder);
                IPCClient.this.notifyAll();
            }
        }
    }
}
