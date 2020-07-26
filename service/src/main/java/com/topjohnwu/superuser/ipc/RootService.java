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

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.SerialExecutorService;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.internal.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * A remote root service using native Android Binder IPC.
 * <p>
 * This class is almost a complete recreation of a bound service running in a root process.
 * Instead of using the original {@code Context.bindService(...)} methods to start and bind
 * to a service, use the provided static methods {@code RootService.bind(...)}.
 * Please note, unlike normal services, RootServices do not have an API similar to
 * {@link Context#startService(Intent)} because RootServices are strictly bound-only.
 * <p>
 * Because the service will not run in the same process as your application, you have to use AIDL
 * to define the IPC interface for communication. Please read the official documentations for more
 * details.
 * @see Service
 * @see <a href="Bound services">https://developer.android.com/guide/components/bound-services</a>
 * @see <a href="Android Interface Definition Language (AIDL)">https://developer.android.com/guide/components/aidl</a>
 */
public abstract class RootService extends ContextWrapper {
    static final String INTENT_VERBOSE_KEY = "verbose_logging";

    static List<IPCClient> active = new ArrayList<>();
    static ExecutorService serialExecutor = new SerialExecutorService();

    /**
     * Connect to a root service, creating if needed.
     * @param intent identifies the service to connect to.
     * @param executor callbacks on ServiceConnection will be called on this executor.
     * @param conn receives information as the service is started and stopped.
     * @see Context#bindService(Intent, int, Executor, ServiceConnection)
     */
    public static void bind(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        serialExecutor.execute(() -> {
            // If no root access, don't even bother
            if (!Shell.rootAccess())
                return;

            Intent intentCopy = new Intent(intent);
            intentCopy.putExtra(INTENT_VERBOSE_KEY, Utils.vLog());

            for (IPCClient client : active) {
                if (client.isSameService(intentCopy)) {
                    client.newConnection(conn, executor);
                    return;
                }
            }

            try {
                IPCClient client = new IPCClient(intentCopy);
                client.newConnection(conn, executor);
                active.add(client);
            } catch (Exception e) {
                Utils.err(e);
            }
        });
    }

    /**
     * Connect to a root service, creating if needed.
     * @param intent identifies the service to connect to.
     * @param conn receives information as the service is started and stopped.
     * @see Context#bindService(Intent, ServiceConnection, int)
     */
    public static void bind(@NonNull Intent intent, @NonNull ServiceConnection conn) {
        bind(intent, UiThreadHandler.executor, conn);
    }

    /**
     * Disconnect from a root service.
     * @param conn the connection interface previously supplied to {@link #bind(Intent, ServiceConnection)}
     * @see Context#unbindService(ServiceConnection)
     */
    public static void unbind(@NonNull ServiceConnection conn) {
        serialExecutor.execute(() -> {
            Iterator<IPCClient> it = active.iterator();
            while (it.hasNext()) {
                IPCClient client = it.next();
                if (client.unbind(conn)) {
                    it.remove();
                    break;
                }
            }
        });
    }

    public RootService() {
        super(null);
    }

    void attach(Context base) {
        attachBaseContext(base);
    }

    @Override
    public final Context getApplicationContext() {
        // Always return ourselves
        return this;
    }

    /**
     * @see Service#onBind(Intent)
     */
    abstract public IBinder onBind(@NonNull Intent intent);

    /**
     * @see Service#onCreate()
     */
    public void onCreate() {}

    /**
     * @see Service#onUnbind(Intent)
     */
    public boolean onUnbind(@NonNull Intent intent) {
        return false;
    }

    /**
     * @see Service#onRebind(Intent)
     */
    public void onRebind(@NonNull Intent intent) {}

    /**
     * @see Service#onDestroy()
     */
    public void onDestroy() {}
}
