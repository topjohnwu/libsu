package com.topjohnwu.libsuexample;

import android.app.Activity;
import android.os.Bundle;
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

    private static final String TAG = "EXAMPLE";

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
            Shell.Sync.su(consoleList, input.getText().toString());
            input.setText("");
        });

        // Run the shell command in the input box asynchronously.
        // Also demonstrates that Async.Callback works
        async_cmd.setOnClickListener(v -> {
            Shell.Async.su(consoleList, consoleList,
                    (out, err) -> Log.d(TAG, "in_async_callback"),
                    input.getText().toString());
            input.setText("");
        });

        // Closing a shell is always synchronous
        close_shell.setOnClickListener(v -> {
            try {
                Shell.getShell().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Load a script from raw resources synchronously
        sync_script.setOnClickListener(v ->
                Shell.Sync.loadScript(consoleList,
                        getResources().openRawResource(R.raw.info)));

        // Load a script from raw resources asynchronously
        async_script.setOnClickListener(v ->
                Shell.Async.loadScript(consoleList,
                        getResources().openRawResource(R.raw.count))
        );

        clear.setOnClickListener(v -> consoleList.clear());

        // We create a CallbackList to update the UI with the Shell output
        consoleList = new CallbackList<String>() {
            private StringBuilder builder = new StringBuilder();

            @Override
            public void onAddElement(String s) {
                builder.append(s).append('\n');
                console.setText(builder);
                sv.postDelayed(() -> sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
            }

            @Override
            public void clear() {
                builder = new StringBuilder();
                handler.post(() -> console.setText(""));
            }
        };
    }
}
