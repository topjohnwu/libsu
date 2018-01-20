package com.topjohnwu.superuser;

import android.os.Handler;

import java.util.Vector;

/**
 * A {@link Vector} to store output of {@link Shell} and call {@link #onShellOutput(String)} when
 * a new output is added to the list.
 * <p>
 * The method {@link #onShellOutput(String)} will be called with a {@link Handler} if the
 * {@link ShellCallbackVector} is constructed in the main thread (UI thread). If you need to update
 * the UI, please construct the new instance in the main thread.
 */

public abstract class ShellCallbackVector extends Vector<String> implements Shell.IShellCallback {

    private Handler handler;

    public ShellCallbackVector() {
        super();
        if (Utils.onMainThread())
            handler = new Handler();
    }

    @Override
    public boolean add(final String s) {
        boolean ret = super.add(s);
        Runnable run = new Runnable() {
            @Override
            public void run() {
                ShellCallbackVector.this.onShellOutput(s);
            }
        };
        if (handler != null) {
            handler.post(run);
        } else {
            run.run();
        }
        return ret;
    }
}
