/*
 * Copyright 2021 John "topjohnwu" Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.topjohnwu.libsuexample;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import com.topjohnwu.libsuexample.databinding.ActivityMainBinding;
import com.topjohnwu.superuser.BusyBoxInstaller;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity implements Handler.Callback {

    public static final String TAG = "EXAMPLE";

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                // BusyBoxInstaller should come first!
                .setInitializers(BusyBoxInstaller.class, ExampleInitializer.class)
        );
    }

    private ITestService testIPC;
    private Messenger remoteMessenger;
    private Messenger myMessenger = new Messenger(new Handler(Looper.getMainLooper(), this));
    private MSGConnection conn = new MSGConnection();
    private boolean daemonTestQueued = false;
    private boolean serviceTestQueued = false;

    private ActivityMainBinding binding;
    private List<String> consoleList = new AppendCallbackList();

    // Demonstrate Shell.Initializer
    static class ExampleInitializer extends Shell.Initializer {

        @Override
        public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
            // Load our init script
            InputStream bashrc = context.getResources().openRawResource(R.raw.bashrc);
            shell.newJob().add(bashrc).exec();
            return true;
        }
    }

    class AIDLConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "daemon onServiceConnected");
            testIPC = ITestService.Stub.asInterface(service);
            if (daemonTestQueued) {
                daemonTestQueued = false;
                testDaemon();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "daemon onServiceDisconnected");
            testIPC = null;
        }
    }

    class MSGConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service onServiceConnected");
            remoteMessenger = new Messenger(service);
            if (serviceTestQueued) {
                serviceTestQueued = false;
                testService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "service onServiceDisconnected");
            remoteMessenger = null;
        }
    }

    private void testDaemon() {
        try {
            consoleList.add("Daemon PID: " + testIPC.getPid());
            consoleList.add("Daemon UID: " + testIPC.getUid());
            String[] cmds = testIPC.readCmdline().split(" ");
            if (cmds.length > 5) {
                cmds = Arrays.copyOf(cmds, 6);
                cmds[5] = "...";
            }
            consoleList.add("/proc/cmdline:");
            consoleList.addAll(Arrays.asList(cmds));
        } catch (RemoteException e) {
            Log.e(TAG, "Remote error", e);
        }
    }

    private void testService() {
        Message message = Message.obtain(null, MSGService.MSG_GETINFO);
        message.replyTo = myMessenger;
        try {
            remoteMessenger.send(message);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote error", e);
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        consoleList.add("Remote PID: " + msg.arg1);
        consoleList.add("Remote UID: " + msg.arg2);
        String cmdline = msg.getData().getString(MSGService.CMDLINE_KEY);
        String[] cmds = cmdline.split(" ");
        if (cmds.length > 5) {
            cmds = Arrays.copyOf(cmds, 6);
            cmds[5] = "...";
        }
        consoleList.add("/proc/cmdline:");
        consoleList.addAll(Arrays.asList(cmds));
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.testSvc.setOnClickListener(v -> {
            if (remoteMessenger == null) {
                serviceTestQueued = true;
                Intent intent = new Intent(this, MSGService.class);
                RootService.bind(intent, conn);
                return;
            }
            testService();
        });

        binding.unbindSvc.setOnClickListener(v -> RootService.unbind(conn));

        binding.testDaemon.setOnClickListener(v -> {
            if (testIPC == null) {
                daemonTestQueued = true;
                Intent intent = new Intent(this, AIDLService.class);
                RootService.bind(intent, new AIDLConnection());
                return;
            }
            testDaemon();
        });

        binding.stopDaemon.setOnClickListener(v -> {
            Intent intent = new Intent(this, AIDLService.class);
            // Use stop here instead of unbind because AIDLService is running as a daemon.
            // To verify whether the daemon actually works, kill the app and try testing the
            // daemon again. You should get the same PID as last time (as it was re-using the
            // previous daemon process), and in AIDLService, onRebind should be called.
            // Note: re-running the app in Android Studio is not the same as kill + relaunch.
            // The root service will kill itself when it detects the client APK has updated.
            RootService.stop(intent);
        });

        binding.closeShell.setOnClickListener(v -> {
            try {
                StressTest.cancel();
                Shell shell = Shell.getCachedShell();
                if (shell != null)
                    shell.close();
            } catch (IOException e) {
                Log.e(TAG, "Error when closing shell", e);
            }
        });

        // test_sync is defined in R.raw.bashrc, pre-loaded in ExampleInitializer
        binding.testSync.setOnClickListener(v ->
                Shell.sh("test_sync").to(consoleList).exec());

        // test_async is defined in R.raw.bashrc, pre-loaded in ExampleInitializer
        binding.testAsync.setOnClickListener(v ->
                Shell.sh("test_async").to(consoleList).submit());

        binding.clear.setOnClickListener(v -> binding.console.setText(""));

        binding.stressTest.setOnClickListener(v -> StressTest.perform(consoleList));
    }

    /**
     * This List does not store the output anywhere. It is used only as an
     * callback API every time a new output is created by the root shell.
     */
    class AppendCallbackList extends CallbackList<String> {
        @Override
        public void onAddElement(String s) {
            binding.console.append(s);
            binding.console.append("\n");
            binding.sv.postDelayed(() -> binding.sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }
    }
}
