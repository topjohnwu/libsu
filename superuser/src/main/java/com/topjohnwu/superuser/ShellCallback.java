package com.topjohnwu.superuser;

import android.os.Handler;

import java.util.AbstractList;

/**
 * An {@link AbstractList} only used as an abstract container to call {@link #onShellOutput(String)}
 * when a new output is added to the list.
 * <p>
 * The method {@link #onShellOutput(String)} will be called in the thread where the
 * {@link ShellCallbackVector} is constructed by using {@link Handler}. If you need to update
 * the UI, please construct the list in the main thread.
 */

public abstract class ShellCallback extends AbstractList<String> implements Shell.IShellCallback {

    private Handler handler;

    public ShellCallback() {
        super();
        handler = new Handler();
    }

    @Override
    public final int size() {
        return 0;
    }

    @Override
    public synchronized boolean add(String s) {
        handler.post(() -> onShellOutput(s));
        return true;
    }

    @Override
    public final String get(int index) {
        return null;
    }
}
