package com.topjohnwu.superuser;

import android.os.Handler;

import java.util.AbstractList;

/**
 * An {@link AbstractList} only used as an abstract container to call {@link #onShellOutput(String)}
 * when a new output is added to the list.
 * <p>
 * The method {@link #onShellOutput(String)} will be called with a {@link Handler} if the
 * {@link ShellCallback} is constructed in the main thread (UI thread). If you need to update
 * the UI, please construct the new instance in the main thread.
 */

public abstract class ShellCallback extends AbstractList<String> implements Shell.IShellCallback {

    private Handler handler = null;

    public ShellCallback() {
        super();
        if (Utils.onMainThread())
            handler = new Handler();
    }

    @Override
    public final int size() {
        return 0;
    }

    @Override
    public synchronized boolean add(final String s) {
        Runnable run = new Runnable() {
            @Override
            public void run() {
                ShellCallback.this.onShellOutput(s);
            }
        };
        if (handler != null) {
            handler.post(run);
        } else {
            run.run();
        }
        return true;
    }

    @Override
    public final String get(int index) {
        return null;
    }
}
