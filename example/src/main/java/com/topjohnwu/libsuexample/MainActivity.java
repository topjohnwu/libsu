package com.topjohnwu.libsuexample;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

    private TextView console;
    private EditText input;
    private ScrollView sv;
    private List<String> consoleList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        console = findViewById(R.id.console);
        input = findViewById(R.id.cmd_input);
        sv = findViewById(R.id.sv);

        Button sync_cmd = findViewById(R.id.sync_cmd);
        Button async_cmd = findViewById(R.id.async_cmd);
        Button close_shell = findViewById(R.id.close_shell);
        Button sync_script = findViewById(R.id.sync_script);
        Button async_script = findViewById(R.id.async_script);
        Button clear = findViewById(R.id.clear);

        // Run the shell command in the input box synchronously
        sync_cmd.setOnClickListener(v -> {
            Shell.sh(input.getText().toString()).to(consoleList).exec();
            input.setText("");
        });

        // Run the shell command in the input box asynchronously.
        // Also demonstrates that Async.Callback works
        async_cmd.setOnClickListener(v -> {
            Shell.sh(input.getText().toString())
                    .to(consoleList)
                    .submit(out -> Log.d(ExampleApp.TAG, "async_cmd_result: " + out.getCode()));
            input.setText("");
        });

        // Closing a shell is always synchronous
        close_shell.setOnClickListener(v -> {
            try {
                Shell shell = Shell.getCachedShell();
                if (shell != null)
                    shell.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Load a script from raw resources synchronously
        sync_script.setOnClickListener(v ->
                Shell.sh(getResources().openRawResource(R.raw.info)).to(consoleList).exec());

        // Load a script from raw resources asynchronously
        async_script.setOnClickListener(v ->
                Shell.sh(getResources().openRawResource(R.raw.count)).to(consoleList).submit());

        clear.setOnClickListener(v -> consoleList.clear());

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
            console.append(s);
            console.append("\n");
            sv.postDelayed(() -> sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }

        @Override
        public synchronized void clear() {
            runOnUiThread(() -> console.setText(""));
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
            console.setText(TextUtils.join("\n", this));
            sv.postDelayed(() -> sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
        }

        @Override
        public synchronized void clear() {
            super.clear();
            runOnUiThread(() -> console.setText(""));
        }
    }
}
