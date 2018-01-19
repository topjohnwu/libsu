package com.topjohnwu.superuser;

import android.os.Handler;

import java.util.Vector;

/**
 * A {@link Vector} to store output of {@link Shell} and call {@link #onShellOutput(String)} when
 * a new output is added to the list.
 * <p>
 * The method {@link #onShellOutput(String)} will be called in the thread where the
 * {@link ShellCallbackVector} is constructed by using {@link Handler}. If you need to update
 * the UI, please construct the list in the main thread.
 */

public abstract class ShellCallbackVector extends Vector<String> implements Shell.IShellCallback {

    private Handler handler;

    public ShellCallbackVector() {
        super();
        handler = new Handler();
    }

    @Override
    public boolean add(String s) {
        boolean ret = super.add(s);
        handler.post(() -> onShellOutput(s));
        return ret;
    }
}
