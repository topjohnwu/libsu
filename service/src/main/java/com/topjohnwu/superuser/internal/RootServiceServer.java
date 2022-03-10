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

import static com.topjohnwu.superuser.internal.RootServerMain.getServiceName;
import static com.topjohnwu.superuser.internal.RootServiceManager.BUNDLE_BINDER_KEY;
import static com.topjohnwu.superuser.internal.RootServiceManager.LOGGING_ENV;
import static com.topjohnwu.superuser.internal.RootServiceManager.MSG_ACK;
import static com.topjohnwu.superuser.internal.RootServiceManager.MSG_STOP;
import static com.topjohnwu.superuser.internal.RootServiceManager.TAG;
import static com.topjohnwu.superuser.internal.Utils.context;
import static com.topjohnwu.superuser.internal.Utils.newArraySet;

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
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RootServiceServer extends IRootServiceManager.Stub {

    private static RootServiceServer mInstance;

    public static RootServiceServer getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RootServiceServer(context);
        }
        return mInstance;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final FileObserver observer;  /* A strong reference is required */
    private final Map<ComponentName, ServiceContainer> activeServices = new ArrayMap<>();
    private final SparseArray<ClientProcess> clients = new SparseArray<>();
    private final boolean isDaemon;

    @SuppressWarnings("rawtypes")
    private RootServiceServer(Context context) {
        Shell.enableVerboseLogging = System.getenv(LOGGING_ENV) != null;
        Utils.context = Utils.getContextImpl(context);
        observer = new AppObserver(new File(context.getPackageCodePath()));
        observer.startWatching();
        if (context instanceof Callable) {
            try {
                Object[] objs = (Object[]) ((Callable) context).call();
                broadcast((int) objs[0], (String) objs[1]);
                isDaemon = (boolean) objs[2];
                if (isDaemon) {
                    // Register ourselves as system service
                    HiddenAPIs.addService(getServiceName(context.getPackageName()), this);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Expected Context to be Callable");
        }
    }

    @Override
    public void connect(IBinder binder, boolean debug) {
        int uid = getCallingUid();
        UiThreadHandler.run(() -> connectInternal(uid, binder, debug));
    }

    private void connectInternal(int uid, IBinder binder, boolean debug) {
        ClientProcess c = clients.get(uid);
        if (c != null)
            return;

        try {
            c = new ClientProcess(binder, uid);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
            return;
        }

        if (debug) {
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

        Bundle bundle = new Bundle();
        bundle.putBinder(BUNDLE_BINDER_KEY, this);

        Message m = Message.obtain();
        m.what = MSG_ACK;
        m.arg1 = isDaemon ? 1 : 0;
        m.setData(bundle);
        try {
            c.m.send(m);
            clients.put(c.mUid, c);
        } catch (RemoteException e) {
            Utils.err(TAG, e);
        } finally {
            m.recycle();
        }
    }

    @SuppressLint("MissingPermission")
    public void broadcast(int uid, String action) {
        // Use the UID argument iff caller is root
        uid = getCallingUid() == 0 ? uid : getCallingUid();
        Utils.log(TAG, "broadcast to uid=" + uid);
        Intent intent = RootServiceManager.getBroadcastIntent(context, action, this);
        if (Build.VERSION.SDK_INT >= 24) {
            UserHandle h = UserHandle.getUserHandleForUid(uid);
            context.sendBroadcastAsUser(intent, h);
        } else {
            context.sendBroadcast(intent);
        }
    }

    @Override
    public IBinder bind(Intent intent) {
        IBinder[] b = new IBinder[1];
        int uid = getCallingUid();
        UiThreadHandler.runAndWait(() -> {
            try {
                b[0] = bindInternal(uid, intent);
            } catch (Exception e) {
                Utils.err(TAG, e);
            }
        });
        return b[0];
    }

    @Override
    public void unbind(ComponentName name) {
        int uid = getCallingUid();
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " unbind");
            unbindService(uid, name);
        });
    }

    @Override
    public void stop(ComponentName name, int uid, String action) {
        // Use the UID argument iff caller is root
        int clientUid = getCallingUid() == 0 ? uid : getCallingUid();
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " stop");
            unbindService(-1, name);
            if (action != null) {
                // If we aren't killed yet, send another broadcast
                broadcast(clientUid, action);
            }
        });
    }

    public void selfStop(ComponentName name) {
        UiThreadHandler.run(() -> {
            Utils.log(TAG, name.getClassName() + " selfStop");
            unbindService(-1, name);
        });
    }

    public void register(RootService service) {
        ServiceContainer s = new ServiceContainer(service);
        activeServices.put(service.getComponentName(), s);
    }

    private IBinder bindInternal(int uid, Intent intent) throws Exception {
        ClientProcess c = clients.get(uid);
        if (c == null)
            return null;

        ComponentName name = intent.getComponent();

        ServiceContainer s = activeServices.get(name);
        if (s == null) {
            Class<?> clz = context.getClassLoader().loadClass(name.getClassName());
            Constructor<?> ctor = clz.getDeclaredConstructor();
            ctor.setAccessible(true);
            HiddenAPIs.attachBaseContext(ctor.newInstance(), context);

            // RootService should be registered after attachBaseContext
            s = activeServices.get(name);
            if (s == null) {
                return null;
            }
        }

        if (s.binder != null) {
            Utils.log(TAG, name.getClassName() + " rebind");
            if (s.rebind)
                s.service.onRebind(s.intent);
        } else {
            Utils.log(TAG, name.getClassName() + " bind");
            s.binder = s.service.onBind(intent);
            s.intent = intent.cloneFilter();
        }
        s.users.add(uid);

        return s.binder;
    }

    private void unbindInternal(ServiceContainer s, int uid, Runnable onDestroy) {
        boolean hadUsers = !s.users.isEmpty();
        s.users.remove(uid);
        if (uid < 0 || s.users.isEmpty()) {
            if (hadUsers) {
                s.rebind = s.service.onUnbind(s.intent);
            }
            if (uid < 0 || !isDaemon) {
                s.service.onDestroy();
                onDestroy.run();

                // Notify all other users
                for (int user : s.users) {
                    ClientProcess c = clients.get(user);
                    if (c == null)
                        continue;
                    Message msg = Message.obtain();
                    msg.what = MSG_STOP;
                    msg.arg1 = isDaemon ? 1 : 0;
                    msg.obj = s.intent.getComponent();
                    try {
                        c.m.send(msg);
                    } catch (RemoteException e) {
                        Utils.err(TAG, e);
                    } finally {
                        msg.recycle();
                    }
                }
            }
        }
        if (activeServices.isEmpty()) {
            // Terminate root process
            System.exit(0);
        }
    }

    private void unbindService(int uid, ComponentName name) {
        ServiceContainer s = activeServices.get(name);
        if (s == null)
            return;
        unbindInternal(s, uid, () -> activeServices.remove(name));
    }

    private void unbindServices(int uid) {
        Iterator<Map.Entry<ComponentName, ServiceContainer>> it =
                activeServices.entrySet().iterator();
        while (it.hasNext()) {
            ServiceContainer s = it.next().getValue();
            if (uid < 0) {
                // App is updated/deleted, all clients will get killed anyways,
                // no need to notify anyone.
                s.users.clear();
            }
            unbindInternal(s, uid, it::remove);
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
                    unbindServices(-1);
                    System.exit(0);
                });
            }
        }
    }

    class ClientProcess extends BinderHolder {

        final Messenger m;
        final int mUid;

        ClientProcess(IBinder b, int uid) throws RemoteException {
            super(b);
            m = new Messenger(b);
            mUid = uid;
        }

        @Override
        protected void onBinderDied() {
            Utils.log(TAG, "Client process terminated, uid=" + mUid);
            clients.remove(mUid);
            unbindServices(mUid);
        }
    }

    static class ServiceContainer {
        final RootService service;
        final Set<Integer> users = newArraySet();

        Intent intent;
        IBinder binder;
        boolean rebind;

        ServiceContainer(RootService s) {
            service = s;
        }
    }
}
