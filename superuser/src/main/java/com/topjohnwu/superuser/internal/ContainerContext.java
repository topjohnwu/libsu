package com.topjohnwu.superuser.internal;

import android.content.Context;
import android.content.ContextWrapper;

import com.topjohnwu.superuser.Shell;

import androidx.annotation.Nullable;

public class ContainerContext extends ContextWrapper implements Shell.Container {

    /**
     * The actual field to save the global {@code Shell} instance.
     */
    private volatile Shell mShell;

    public ContainerContext(Context base) {
        super(base);
        Shell.Config.setContainer(this);
    }

    @Nullable
    @Override
    public Shell getShell() {
        return mShell;
    }

    @Override
    public void setShell(@Nullable Shell shell) {
        mShell = shell;
    }
}
