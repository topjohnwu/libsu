package com.topjohnwu.libsuexample;

import com.topjohnwu.superuser.BusyBox;
import com.topjohnwu.superuser.Shell;

public class ExampleApp extends Shell.ContainerApp {

    @Override
    public void onCreate() {
        super.onCreate();
        // Set flags
        Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.verboseLogging(BuildConfig.DEBUG);
        // Use internal busybox
        BusyBox.setup(this);
    }
}
