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

package com.topjohnwu.superuser.internal;

import static com.topjohnwu.superuser.internal.RootServerMain.CMDLINE_START_DAEMON;
import static com.topjohnwu.superuser.internal.RootServerMain.CMDLINE_START_SERVICE;
import static com.topjohnwu.superuser.internal.RootServerMain.CMDLINE_STOP_SERVICE;
import static com.topjohnwu.superuser.ipc.RootService.CATEGORY_DAEMON_MODE;

import android.Manifest;
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
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Runs in the non-root (client) process.
 *
 * Starts the root process and manages connections with the remote process.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceManager implements Handler.Callback {

    private static RootServiceManager mInstance;

    static final String TAG = "IPC";
    static final String LOGGING_ENV = "LIBSU_VERBOSE_LOGGING";
    static final String DEBUG_ENV = "LIBSU_DEBUGGER";

    static final int MSG_STOP = 1;

    private static final String BUNDLE_BINDER_KEY = "binder";
    private static final String INTENT_BUNDLE_KEY = "extra.bundle";
    private static final String INTENT_DAEMON_KEY = "extra.daemon";
    private static final String RECEIVER_BROADCAST = "com.topjohnwu.superuser.RECEIVER_BROADCAST";
    private static final String API_27_DEBUG =
            "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y " +
            "-Xcompiler-option --debuggable";
    private static final String API_28_DEBUG =
            "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y " +
            "-Xcompiler-option --debuggable";
    private static final String JVMTI_ERROR = " \n" +
            "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n" +
            "! Warning: JVMTI agent is enabled. Please enable the !\n" +
            "! 'Always install with package manager' option in    !\n" +
            "! Android Studio. For more details and information,  !\n" +
            "! check out RootService's Javadoc.                   !\n" +
            "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n";

    private static final int REMOTE_EN_ROUTE = 1 << 0;
    private static final int DAEMON_EN_ROUTE = 1 << 1;
    private static final int RECEIVER_REGISTERED = 1 << 2;

    public static RootServiceManager getInstance() {
        if (mInstance == null) {
            mInstance = new RootServiceManager();
        }
        return mInstance;
    }

    @SuppressLint("WrongConstant")
    static Intent getBroadcastIntent(IBinder binder, boolean isDaemon) {
        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, binder);
        return new Intent(RECEIVER_BROADCAST)
                .setPackage(Utils.getContext().getPackageName())
                .addFlags(HiddenAPIs.FLAG_RECEIVER_FROM_SHELL)
                .putExtra(INTENT_DAEMON_KEY, isDaemon)
                .putExtra(INTENT_BUNDLE_KEY, bundle);
    }

    private static void enforceMainThread() {
        if (!ShellUtils.onMainThread()) {
            throw new IllegalStateException("This method can only be called on the main thread");
        }
    }

    @NonNull
    private static ServiceKey parseIntent(Intent intent) {
        ComponentName name = intent.getComponent();
        if (name == null) {
            throw new IllegalArgumentException("The intent does not have a component set");
        }
        if (!name.getPackageName().equals(Utils.getContext().getPackageName())) {
            throw new IllegalArgumentException("RootServices outside of the app are not supported");
        }
        return new ServiceKey(name, intent.hasCategory(CATEGORY_DAEMON_MODE));
    }

    private RemoteProcess mRemote;
    private RemoteProcess mDaemon;

    private int flags = 0;

    private final List<BindTask> pendingTasks = new ArrayList<>();
    private final Map<ServiceKey, RemoteServiceRecord> services = new ArrayMap<>();
    private final Map<ServiceConnection, ConnectionRecord> connections = new ArrayMap<>();

    private RootServiceManager() {}

    @SuppressLint("InlinedApi")
    private Shell.Task startRootProcess(ComponentName name, String action) {
        Context context = Utils.getContext();

        if ((flags & RECEIVER_REGISTERED) == 0) {
            // Register receiver to receive binder from root process
            IntentFilter filter = new IntentFilter(RECEIVER_BROADCAST);
            // Guard the receiver behind permission BROADCAST_PACKAGE_REMOVED. This permission
            // is not obtainable by normal apps, making the receiver effectively non-exported.
            // On Android 13+, we can also rely on the flag RECEIVER_NOT_EXPORTED.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.registerReceiver(new ServiceReceiver(), filter,
                        Manifest.permission.BROADCAST_PACKAGE_REMOVED, null,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(new ServiceReceiver(), filter,
                        Manifest.permission.BROADCAST_PACKAGE_REMOVED, null);
            }
            flags |= RECEIVER_REGISTERED;
        }

        return (stdin, stdout, stderr) -> {
            if (Utils.hasStartupAgents(context)) {
                Log.e(TAG, JVMTI_ERROR);
            }

            Context ctx = Utils.getDeContext();
            File mainJar = new File(ctx.getCacheDir(), "main.jar");

            // Dump main.jar as trampoline
            try (InputStream in = ctx.getResources().getAssets().open("main.jar");
                 OutputStream out = new FileOutputStream(mainJar)) {
                Utils.pump(in, out);
            }

            String env = "";
            String params = "";

            if (Utils.vLog()) {
                env = LOGGING_ENV + "=1 ";
            }

            // Only support debugging on SDK >= 27
            if (Build.VERSION.SDK_INT >= 27 && Debug.isDebuggerConnected()) {
                env += DEBUG_ENV + "=1 ";
                // Reference of the params to start jdwp:
                // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
                if (Build.VERSION.SDK_INT == 27) {
                    params = API_27_DEBUG;
                } else {
                    params = API_28_DEBUG;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                params += " -Xnoimage-dex2oat";
            }

            final String niceNameCmd;
            switch (action) {
                case CMDLINE_START_SERVICE:
                    niceNameCmd = String.format(Locale.ROOT, "--nice-name=%s:root:%d",
                            ctx.getPackageName(), Process.myUid() / 100000);
                    break;
                case CMDLINE_START_DAEMON:
                    niceNameCmd = "--nice-name=" + ctx.getPackageName() + ":root:daemon";
                    break;
                default:
                    niceNameCmd = "";
                    break;
            }

            // We cannot readlink /proc/self/exe on old kernels
            String app_process = "/system/bin/app_process";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                app_process += Utils.isProcess64Bit() ? "64" : "32";
            }
            String cmd = String.format(Locale.ROOT,
                    "(%s CLASSPATH=%s %s %s /system/bin %s " +
                    "com.topjohnwu.superuser.internal.RootServerMain '%s' %d %s >/dev/null 2>&1)&",
                    env, mainJar, app_process, params, niceNameCmd,
                    name.flattenToString(),   // args[0]
                    Process.myUid(),          // args[1]
                    action);                  // args[2]

            Utils.log(TAG, cmd);
            // Write command to stdin
            byte[] bytes = cmd.getBytes(StandardCharsets.UTF_8);
            stdin.write(bytes);
            stdin.write('\n');
            stdin.flush();
            // Since all output for the command is redirected to /dev/null and
            // the command runs in the background, we don't need to wait and
            // can just return.
        };
    }

    // Returns null if binding is done synchronously, or else return key
    private ServiceKey bindInternal(Intent intent, Executor executor, ServiceConnection conn) {
        enforceMainThread();

        // Local cache
        ServiceKey key = parseIntent(intent);
        RemoteServiceRecord s = services.get(key);
        if (s != null) {
            connections.put(conn, new ConnectionRecord(s, executor));
            s.refCount++;
            IBinder binder = s.binder;
            executor.execute(() -> conn.onServiceConnected(key.getName(), binder));
            return null;
        }

        RemoteProcess p = key.isDaemon() ? mDaemon : mRemote;
        if (p == null)
            return key;

        try {
            IBinder binder = p.mgr.bind(intent);
            if (binder != null) {
                s = new RemoteServiceRecord(key, binder, p);
                connections.put(conn, new ConnectionRecord(s, executor));
                services.put(key, s);
                executor.execute(() -> conn.onServiceConnected(key.getName(), binder));
            } else if (Build.VERSION.SDK_INT >= 28) {
                executor.execute(() -> conn.onNullBinding(key.getName()));
            }
        } catch (RemoteException e) {
            Utils.err(TAG, e);
            p.binderDied();
            return key;
        }

        return null;
    }

    public Shell.Task createBindTask(Intent intent, Executor executor, ServiceConnection conn) {
        ServiceKey key = bindInternal(intent, executor, conn);
        if (key != null) {
            pendingTasks.add(() -> bindInternal(intent, executor, conn) == null);
            int mask = key.isDaemon() ? DAEMON_EN_ROUTE : REMOTE_EN_ROUTE;
            if ((flags & mask) == 0) {
                flags |= mask;
                String action = key.isDaemon() ? CMDLINE_START_DAEMON : CMDLINE_START_SERVICE;
                return startRootProcess(key.getName(), action);
            }
        }
        return null;
    }

    public void unbind(@NonNull ServiceConnection conn) {
        enforceMainThread();

        ConnectionRecord r = connections.remove(conn);
        if (r != null) {
            RemoteServiceRecord s = r.getService();
            s.refCount--;
            if (s.refCount == 0) {
                // Actually close the service
                services.remove(s.key);
                try {
                    s.host.mgr.unbind(s.key.getName());
                } catch (RemoteException e) {
                    Utils.err(TAG, e);
                }
            }
            r.disconnect(conn);
        }
    }

    private void dropConnections(Predicate predicate) {
        Iterator<Map.Entry<ServiceConnection, ConnectionRecord>> it =
                connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ServiceConnection, ConnectionRecord> e = it.next();
            ConnectionRecord r = e.getValue();
            if (predicate.eval(r.getService())) {
                r.disconnect(e.getKey());
                it.remove();
            }
        }
    }

    private void onServiceStopped(ServiceKey key) {
        RemoteServiceRecord s = services.remove(key);
        if (s != null)
            dropConnections(s::equals);
    }

    public Shell.Task createStopTask(Intent intent) {
        enforceMainThread();

        ServiceKey key = parseIntent(intent);
        RemoteProcess p = key.isDaemon() ? mDaemon : mRemote;
        if (p == null) {
            if (key.isDaemon()) {
                // Start a new root process to stop daemon
                return startRootProcess(key.getName(), CMDLINE_STOP_SERVICE);
            }
            return null;
        }

        try {
            p.mgr.stop(key.getName(), -1);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
        }

        onServiceStopped(key);
        return null;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_STOP) {
            onServiceStopped(new ServiceKey((ComponentName) msg.obj, msg.arg1 != 0));
        }
        return false;
    }

    private static class ServiceKey extends Pair<ComponentName, Boolean> {
        ServiceKey(ComponentName name, boolean isDaemon) {
            super(name, isDaemon);
        }
        ComponentName getName() { return first; }
        boolean isDaemon() { return second; }
    }

    private static class ConnectionRecord extends Pair<RemoteServiceRecord, Executor> {
        ConnectionRecord(RemoteServiceRecord s, Executor e) {
            super(s, e);
        }
        RemoteServiceRecord getService() { return first; }
        void disconnect(ServiceConnection conn) {
            second.execute(() -> conn.onServiceDisconnected(first.key.getName()));
        }
    }

    private class RemoteProcess extends BinderHolder {

        final IRootServiceManager mgr;

        RemoteProcess(IRootServiceManager s) throws RemoteException {
            super(s.asBinder());
            mgr = s;
        }

        @Override
        protected void onBinderDied() {
            if (mRemote == this)
                mRemote = null;
            if (mDaemon == this)
                mDaemon = null;

            Iterator<RemoteServiceRecord> sit = services.values().iterator();
            while (sit.hasNext()) {
                if (sit.next().host == this) {
                    sit.remove();
                }
            }
            dropConnections(s -> s.host == this);
        }
    }

    private static class RemoteServiceRecord {
        final ServiceKey key;
        final IBinder binder;
        final RemoteProcess host;
        int refCount = 1;

        RemoteServiceRecord(ServiceKey key, IBinder binder, RemoteProcess host) {
            this.key = key;
            this.binder = binder;
            this.host = host;
        }
    }

    private class ServiceReceiver extends BroadcastReceiver {

        private final Messenger m;

        ServiceReceiver() {
            // Create messenger to receive service stop notification
            Handler h = new Handler(Looper.getMainLooper(), RootServiceManager.this);
            m = new Messenger(h);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra(INTENT_BUNDLE_KEY);
            if (bundle == null)
                return;
            IBinder binder = bundle.getBinder(BUNDLE_BINDER_KEY);
            if (binder == null)
                return;

            IRootServiceManager mgr = IRootServiceManager.Stub.asInterface(binder);
            try {
                mgr.connect(m.getBinder());
                RemoteProcess p = new RemoteProcess(mgr);
                if (intent.getBooleanExtra(INTENT_DAEMON_KEY, false)) {
                    mDaemon = p;
                    flags &= ~DAEMON_EN_ROUTE;
                } else {
                    mRemote = p;
                    flags &= ~REMOTE_EN_ROUTE;
                }
                for (int i = pendingTasks.size() - 1; i >= 0; --i) {
                    if (pendingTasks.get(i).run()) {
                        pendingTasks.remove(i);
                    }
                }
            } catch (RemoteException e) {
                Utils.err(TAG, e);
            }
        }
    }

    private interface BindTask {
        boolean run();
    }

    private interface Predicate {
        boolean eval(RemoteServiceRecord s);
    }
}
