package com.topjohnwu.libsuexample;

import com.topjohnwu.superuser.Shell;

public class ExampleApp extends Shell.ContainerApp {

    public ExampleApp() {
        // Set flags
        Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.verboseLogging(BuildConfig.DEBUG);
    }
}
