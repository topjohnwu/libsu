/*
 * Copyright 2020 John "topjohnwu" Wu
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
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import com.topjohnwu.libsuexample.databinding.ActivityMainBinding;
import com.topjohnwu.superuser.BusyBoxInstaller;
import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

    public static final String TAG = "EXAMPLE";

    static {
        // Configuration
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        Shell.Config.setInitializers(BusyBoxInstaller.class, ExampleInitializer.class);
    }

    private List<String> consoleList;
    private ActivityMainBinding binding;

    private ITestService testIPC;
    private RootConnection conn = new RootConnection();
    private boolean queuedTest = false;

    // Demonstrate Shell.Initializer
    static class ExampleInitializer extends Shell.Initializer {

        @Override
        public boolean onInit(@NonNull Context context, @NonNull Shell shell) {
            Log.d(TAG, "onInit");
            return true;
        }
    }

    // Demonstrate RootService (daemon mode)
    static class ExampleService extends RootService {

        static {
            // Only load in root process
            if (Process.myUid() == 0)
                System.loadLibrary("native-lib");
        }

        native int nativeGetUid();
        native String nativeReadFile(String file);

        @Override
        public void onRebind(@NonNull Intent intent) {
            Log.d(TAG, "onRebind, daemon process reused");
        }

        @Override
        public IBinder onBind(@NonNull Intent intent) {
            return new ITestService.Stub() {
                @Override
                public int getPid() {
                    return Process.myPid();
                }

                @Override
                public int getUid() {
                    return nativeGetUid();
                }

                @Override
                public String readCmdline() {
                    return nativeReadFile("/proc/cmdline");
                }
            };
        }

        @Override
        public boolean onUnbind(@NonNull Intent intent) {
            // Enable daemon mode
            Log.d(TAG, "onUnbind, client process unbound");
            return true;
        }
    }

    class RootConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            testIPC = ITestService.Stub.asInterface(service);
            if (queuedTest) {
                queuedTest = false;
                testService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            testIPC = null;
        }
    }

    private void testService() {
        try {
            consoleList.add("Remote PID: " + testIPC.getPid());
            consoleList.add("Remote UID: " + testIPC.getUid());
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.testSvc.setOnClickListener(v -> {
            if (testIPC == null) {
                queuedTest = true;
                Intent intent = new Intent(this, ExampleService.class);
                RootService.bind(intent, conn);
                return;
            }
            testService();
        });

        binding.stopSvc.setOnClickListener(v -> {
            Intent intent = new Intent(this, ExampleService.class);
            RootService.stop(intent);
        });

        // Closing a shell is always synchronous
        binding.closeShell.setOnClickListener(v -> {
            try {
                Shell shell = Shell.getCachedShell();
                if (shell != null)
                    shell.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Load a script from raw resources synchronously
        binding.syncScript.setOnClickListener(v ->
                Shell.sh(getResources().openRawResource(R.raw.info)).to(consoleList).exec());

        // Load a script from raw resources asynchronously
        binding.asyncScript.setOnClickListener(v ->
                Shell.sh(getResources().openRawResource(R.raw.count)).to(consoleList).submit());

        binding.clear.setOnClickListener(v -> consoleList.clear());

        /* Create a CallbackList to update the UI with Shell output
         * Here I demonstrate 2 ways to implement a CallbackList
         *
         * Both ContainerCallbackList or AppendCallbackList will have
         * the same behavior.
         */
        consoleList = new AppendCallbackList();
        // consoleList = new ContainerCallbackList(new ArrayList<>());
    }

    /**
     * This class does not store the output anywhere. It is used only as an
     * callback API every time a new output is created.
     */
    private class AppendCallbackList extends CallbackList<String> {

        @Override
        public void onAddElement(String s) {
            binding.console.append(s);
            binding.console.append("\n");
            binding.sv.postDelayed(() -> binding.sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }

        @Override
        public synchronized void clear() {
            runOnUiThread(() -> binding.console.setText(""));
        }
    }

    /**
     * This class stores all outputs to the provided List<String> every time
     * a new output is created.
     *
     * To make it behave exactly the same as AppendCallbackList, I joined
     * all output together with newline and set the whole TextView with the result.
     * It doesn't make sense to do this in this scenario since we do not actually
     * need to store the output. However, it is here to demonstrate that CallbackList
     * can also be used to store outputs and behaves just like normal List<String> if
     * provided a container for storage.
     */
    private class ContainerCallbackList extends CallbackList<String> {

        private ContainerCallbackList(List<String> l) {
            super(l);
        }

        @Override
        public void onAddElement(String s) {
            binding.console.setText(TextUtils.join("\n", this));
            binding.sv.postDelayed(() -> binding.sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }

        @Override
        public synchronized void clear() {
            super.clear();
            runOnUiThread(() -> binding.console.setText(""));
        }
    }
}
