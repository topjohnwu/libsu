package com.topjohnwu.libsuexample;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.BusyBox;
import com.topjohnwu.superuser.Shell;

public class ExampleApp extends Shell.ContainerApp {

    public static final String TAG = "EXAMPLE";

    @Override
    public void onCreate() {
        super.onCreate();
        // Use internal busybox
        BusyBox.setup(this);
        // Set flags
        Shell.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.verboseLogging(BuildConfig.DEBUG);
        Shell.setInitializer(ExampleInitializer.class);
    }

    // Demonstrate Shell.Initializer
    private static class ExampleInitializer extends Shell.Initializer {
        @Override
        public boolean onShellInit(Context context, Shell shell) {
            Log.d(TAG, "onShellInit");
            return true;
        }

        @Override
        public boolean onRootShellInit(Context context, Shell shell) {
            Log.d(TAG, "onRootShellInit");
            return true;
        }
    }
}
