package com.topjohnwu.superuser.internal;

import com.topjohnwu.superuser.Shell;

import androidx.annotation.Nullable;

public class DefaultContainer implements Shell.Container {

    public static final DefaultContainer CONTAINER = new DefaultContainer();

    private volatile Shell shell;

    private DefaultContainer() {}

    @Nullable
    @Override
    public Shell getShell() {
        return shell;
    }

    @Override
    public void setShell(@Nullable Shell s) {
        shell = s;
    }
}
