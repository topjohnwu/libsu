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

package com.topjohnwu.superuser.ipc;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.RootServiceManager;
import com.topjohnwu.superuser.internal.RootServiceServer;
import com.topjohnwu.superuser.internal.UiThreadHandler;
import com.topjohnwu.superuser.internal.Utils;

import java.util.concurrent.Executor;

/**
 * A remote root service using native Android Binder IPC.
 * <p>
 * Pro tip: while developing an app with RootServices, modify the run/debug configuration and check
 * the "Always install with package manager" option if testing on Android 11+, or else the code
 * changes will not be reflected after Android Studio's deployment.
 * <p>
 * This class is almost a complete recreation of a bound service running in a root process.
 * Instead of using the original {@code Context.bindService(...)} methods to start and bind
 * to a service, use the provided static methods {@code RootService.bind(...)}.
 * Because the service will not run in the same process as your application, you have to use either
 * {@link Messenger} or AIDL to define the IPC interface for communication. Please read the
 * official documentations for more details.
 * <p>
 * Even though a {@code RootService} is a {@link Context} of the app package, since we are running
 * in a root environment and the ContextImpl is not constructed in the "normal" way, the
 * functionality of this context is much more limited compared to normal non-root cases. Be aware
 * of this and do not assume all context methods will work, many will result in Exceptions.
 * <p>
 * <strong>Daemon mode:</strong><br>
 * By default, the root service will be destroyed when no components are bound to it
 * (including when the non-root app process is terminated). However, if you'd like to have
 * the root service run independently of the app's lifecycle (aka "Daemon Mode"), override the
 * method {@link #onUnbind(Intent)} and return {@code true}. Subsequent bindings will call
 * the {@link #onRebind(Intent)} method.
 * <p>
 * All RootServices of an app will run in the same root process, as root processes are launched
 * per package. The root service process will terminate in the following conditions:
 * <ul>
 *     <li>When the application is updated/deleted</li>
 *     <li>When all services are destroyed (after {@link #onDestroy()} is called)</li>
 *     <li>Non-daemon services will be automatically destroyed when all clients are
 *         unbounded or terminated</li>
 *     <li>Daemon services will only be destroyed when the client called {@link #stop(Intent)}
 *         or the root service called {@link #stopSelf()}</li>
 * </ul>
 * The library will NOT attempt to automatically restart and bind to services under any circumstance.
 * @see <a href="https://developer.android.com/guide/components/bound-services">Bound services</a>
 * @see <a href="https://developer.android.com/guide/components/aidl">Android Interface Definition Language (AIDL)</a>
 */
public abstract class RootService extends ContextWrapper {

    /**
     * Connect to a root service, creating it if needed.
     * @param intent identifies the service to connect to.
     * @param executor callbacks on ServiceConnection will be called on this executor.
     * @param conn receives information as the service is started and stopped.
     * @see Context#bindService(Intent, int, Executor, ServiceConnection)
     */
    @MainThread
    public static void bind(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        Runnable r = createBindTask(intent, executor, conn);
        if (r != null) {
            Shell.EXECUTOR.execute(r);
        }
    }

    /**
     * Connect to a root service, creating it if needed.
     * @param intent identifies the service to connect to.
     * @param conn receives information as the service is started and stopped.
     * @see Context#bindService(Intent, ServiceConnection, int)
     */
    @MainThread
    public static void bind(@NonNull Intent intent, @NonNull ServiceConnection conn) {
        bind(intent, UiThreadHandler.executor, conn);
    }

    /**
     * Connect to a root service, creating it if needed.
     * <p>
     * This method is useful if you want to precisely manage which background thread and the
     * timing to do I/O operations and execute root commands for creating a new root process.
     * <p>
     * Binding will NOT happen if the developer does not run the returned {@link Runnable}.
     * @return a {@link Runnable} instance on which a new root process will be launched upon
     * calling {@link Runnable#run()}. If there is no need for creating a new root process,
     * {@code null} is returned.
     * @see #bind(Intent, Executor, ServiceConnection)
     */
    @MainThread
    @Nullable
    public static Runnable createBindTask(
            @NonNull Intent intent,
            @NonNull Executor executor,
            @NonNull ServiceConnection conn) {
        return RootServiceManager.getInstance().createBindTask(intent, executor, conn);
    }

    /**
     * Disconnect from a root service.
     * @param conn the connection interface previously supplied to {@link #bind(Intent, ServiceConnection)}
     * @see Context#unbindService(ServiceConnection)
     */
    @MainThread
    public static void unbind(@NonNull ServiceConnection conn) {
        RootServiceManager.getInstance().unbind(conn);
    }

    /**
     * Force stop a root service.
     * <p>
     * Since root services are bound only, unlike {@link Context#stopService(Intent)}, this
     * method is used to immediately stop a root service regardless of its state.
     * Only use this method to stop a daemon root service; for normal root services please use
     * {@link #unbind(ServiceConnection)} instead as this method could potentially end up starting
     * an additional root process to make sure daemon services are stopped.
     * @param intent identifies the service to stop.
     */
    @MainThread
    public static void stop(@NonNull Intent intent) {
        RootServiceManager.getInstance().stop(intent);
    }

    public RootService() {
        super(null);
    }

    @Override
    @CallSuper
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Utils.getContextImpl(base));
        RootServiceServer.getInstance(base).register(this);
        onCreate();
    }

    /**
     * Return the component name that will be used for service lookup.
     * <p>
     * Overriding this method is only for very unusual situations when a different
     * component name other than the actual class name is desired.
     * @return the desired component name
     */
    @NonNull
    public ComponentName getComponentName() {
        return new ComponentName(this, getClass());
    }

    @Override
    public final Context getApplicationContext() {
        return getBaseContext();
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

    /**
     * Force stop this root service process.
     * <p>
     * This is the same as calling {@link #stop(Intent)} for this particular service.
     */
    public final void stopSelf() {
        RootServiceServer.getInstance(this).selfStop(getComponentName());
    }
}
