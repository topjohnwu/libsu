package com.topjohnwu.libsuexample;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.BusyBoxInstaller;
import com.topjohnwu.superuser.ContainerApp;
import com.topjohnwu.superuser.Shell;

import androidx.annotation.NonNull;

public class ExampleApp extends ContainerApp {

    public static final String TAG = "EXAMPLE";

    static {
        // Use internal busybox
        Shell.Config.addInitializers(BusyBoxInstaller.class);
        // Configuration
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.addInitializers(ExampleInitializer.class);
    }

    // Demonstrate Shell.Initializer
    private static class ExampleInitializer extends Shell.Initializer {

        @Override
        public boolean onInit(Context context, @NonNull Shell shell) {
            Log.d(TAG, "onInit");
            return true;
        }
    }
}
