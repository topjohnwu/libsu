package com.topjohnwu.superuser.internal;

import android.content.Context;
import android.content.ContextWrapper;

import com.topjohnwu.superuser.Shell;

import androidx.annotation.Nullable;

class AutoContainer implements Shell.Container {

    @Nullable
    @Override
    public Shell getShell() {
        return injectContainer().getShell();
    }

    @Override
    public void setShell(@Nullable Shell shell) {
        injectContainer().setShell(shell);
    }

    private Shell.Container injectContainer() {
        ContextWrapper ctx = InternalUtils.getContext();
        ContainerContext container = new ContainerContext(ctx.getBaseContext());
        InternalUtils.replaceBaseContext(ctx, container);
        Shell.Config.setContainer(container);
        return container;
    }

    private class ContainerContext extends ContextWrapper implements Shell.Container {

        private volatile Shell mShell;

        ContainerContext(Context base) {
            super(base);
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
}
