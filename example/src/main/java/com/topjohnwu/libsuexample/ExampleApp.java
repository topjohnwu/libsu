package com.topjohnwu.libsuexample;

import android.app.Application;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellContainer;

/**
 * The {@link Application} of the Example app.
 * <p>
 * We implement {@link ShellContainer} to the {@link Application} of
 * the app, which means that we would love our root shell to live as long as the
 * application itself.
 */
public class ExampleApp extends Application implements ShellContainer {

    /**
     * A shell instance living alongside the lifecycle of {@link ExampleApp}
     */
    public Shell mShell;

    @Override
    public void onCreate() {
        super.onCreate();
        // Enable verbose logging flags
        Shell.addFlags(Shell.FLAG_VERBOSE_LOGGING | Shell.FLAG_REDIRECT_STDERR);
        Shell.setGlobalContainer(this);
    }

    @Override
    public Shell getShell() {
        return mShell;
    }

    @Override
    public void setShell(Shell shell) {
        mShell = shell;
    }
}
