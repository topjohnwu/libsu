package com.topjohnwu.libsuexample;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.BusyBox;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ContainerApp;

public class ExampleApp extends ContainerApp {

    public static final String TAG = "EXAMPLE";

    @Override
    public void onCreate() {
        super.onCreate();
        // Use internal busybox
        BusyBox.setup(this);
        // Configuration
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.setInitializer(ExampleInitializer.class);
    }

    // Demonstrate Shell.Initializer
    private static class ExampleInitializer extends Shell.Initializer {

        @Override
        public boolean onInit(Context context, Shell shell) {
            Log.d(TAG, "onInit");
            return true;
        }
    }
}
