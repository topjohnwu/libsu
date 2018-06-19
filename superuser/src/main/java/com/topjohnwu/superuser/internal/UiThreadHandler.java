package com.topjohnwu.superuser.internal;

import android.os.Handler;
import android.os.Looper;

import com.topjohnwu.superuser.ShellUtils;

public class UiThreadHandler {
    private static Handler handler = new Handler(Looper.getMainLooper());
    public static void run(Runnable r) {
        if (ShellUtils.onMainThread()) {
            r.run();
        } else {
            handler.post(r);
        }
    }
    public static void runSynchronized(Object lock, Runnable r) {
        if (ShellUtils.onMainThread()) {
            synchronized (lock) { r.run(); }
        } else {
            handler.post(() -> { synchronized (lock) { r.run(); } });
        }
    }
}
