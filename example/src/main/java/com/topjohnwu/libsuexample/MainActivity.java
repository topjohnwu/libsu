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

    private final Messenger me = new Messenger(new Handler(Looper.getMainLooper(), this));
    private final List<String> consoleList = new AppendCallbackList();

    private ActivityMainBinding binding;

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

    private AIDLConnection aidlConn;
    private AIDLConnection daemonConn;

    class AIDLConnection implements ServiceConnection {

        private final boolean isDaemon;

        AIDLConnection(boolean b) {
            isDaemon = b;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "AIDL onServiceConnected");
            if (isDaemon) daemonConn = this;
            else aidlConn = this;
            refreshUI();

            ITestService ipc = ITestService.Stub.asInterface(service);
            try {
                consoleList.add("AIDL PID : " + ipc.getPid());
                consoleList.add("AIDL UID : " + ipc.getUid());
                consoleList.add("AIDL UUID: " + ipc.getUUID());
            } catch (RemoteException e) {
                Log.e(TAG, "Remote error", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "AIDL onServiceDisconnected");
            if (isDaemon) daemonConn = null;
            else aidlConn = null;
            refreshUI();
        }
    }

    private MSGConnection msgConn;

    class MSGConnection implements ServiceConnection {

        private Messenger m;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "MSG onServiceConnected");
            m = new Messenger(service);
            msgConn = this;
            refreshUI();

            Message msg = Message.obtain(null, MSGService.MSG_GETINFO);
            msg.replyTo = me;
            try {
                m.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote error", e);
            } finally {
                msg.recycle();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "MSG onServiceDisconnected");
            msgConn = null;
            refreshUI();
        }

        void stop() {
            if (m == null)
                return;
            Message msg = Message.obtain(null, MSGService.MSG_STOP);
            try {
                m.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote error", e);
            } finally {
                msg.recycle();
            }
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        consoleList.add("MSG PID  : " + msg.arg1);
        consoleList.add("MSG UID  : " + msg.arg2);
        String uuid = msg.getData().getString(MSGService.UUID_KEY);
        consoleList.add("MSG UUID : " + uuid);
        return false;
    }

    private void refreshUI() {
        binding.aidlSvc.setText(aidlConn == null ? "Bind AIDL" : "Unbind AIDL");
        binding.msgSvc.setText(msgConn == null ? "Bind MSG" : "Unbind MSG");
        binding.testDaemon.setText(daemonConn == null ? "Bind Daemon" : "Unbind Daemon");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bind to a root service; IPC via AIDL
        binding.aidlSvc.setOnClickListener(v -> {
            if (aidlConn == null) {
                Intent intent = new Intent(this, AIDLService.class);
                RootService.bind(intent, new AIDLConnection(false));
            } else {
                RootService.unbind(aidlConn);
            }
        });

        // Bind to a root service; IPC via Messages
        binding.msgSvc.setOnClickListener(v -> {
            if (msgConn == null) {
                Intent intent = new Intent(this, MSGService.class);
                RootService.bind(intent, new MSGConnection());
            } else {
                RootService.unbind(msgConn);
            }
        });

        // Send a message to service and ask it to stop itself to test stopSelf()
        binding.selfStop.setOnClickListener(v -> {
            if (msgConn != null) {
                msgConn.stop();
            }
        });

        // To verify whether the daemon actually works, kill the app and try testing the
        // daemon again. You should get the same PID as last time (as it was re-using the
        // previous daemon process), and in AIDLService, onRebind should be called.
        // Note: re-running the app in Android Studio is not the same as kill + relaunch.
        // The root process will kill itself when it detects the client APK has updated.

        // Bind to a daemon root service
        binding.testDaemon.setOnClickListener(v -> {
            if (daemonConn == null) {
                Intent intent = new Intent(this, AIDLService.class);
                intent.addCategory(RootService.CATEGORY_DAEMON_MODE);
                RootService.bind(intent, new AIDLConnection(true));
            } else {
                RootService.unbind(daemonConn);
            }
        });

        // Test the stop API
        binding.stopDaemon.setOnClickListener(v -> {
            Intent intent = new Intent(this, AIDLService.class);
            intent.addCategory(RootService.CATEGORY_DAEMON_MODE);
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
                Shell.cmd("test_sync").to(consoleList).exec());

        // test_async is defined in R.raw.bashrc, pre-loaded in ExampleInitializer
        binding.testAsync.setOnClickListener(v ->
                Shell.cmd("test_async").to(consoleList).submit());

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
