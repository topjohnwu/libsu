package com.topjohnwu.libsuexample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.topjohnwu.superuser.CallbackList;
import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;

import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "EXAMPLE";

    private TextView console;
    private EditText input;
    private ScrollView sv;
    private List<String> callback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        console = findViewById(R.id.console);
        input = findViewById(R.id.cmd_input);
        sv = findViewById(R.id.sv);

        Button sync_cmd = findViewById(R.id.sync_cmd);
        Button async_cmd = findViewById(R.id.async_cmd);
        Button raw_cmd = findViewById(R.id.raw_cmd);
        Button sync_script = findViewById(R.id.sync_script);
        Button async_script = findViewById(R.id.async_script);
        Button clear = findViewById(R.id.clear);

        // Run the shell command in the input box synchronously
        sync_cmd.setOnClickListener(v -> {
            Shell.su(callback, input.getText().toString());
            input.setText("");
        });

        // Run the shell command in the input box asynchronously
        async_cmd.setOnClickListener(v -> {
            Shell.su_async(callback, input.getText().toString());
            input.setText("");
        });

        // Run the shell command in the input box ignoring output asynchronously
        raw_cmd.setOnClickListener(v -> {
            Shell.su_raw(input.getText().toString());
            input.setText("");
        });

        // Load a script from raw resources
        sync_script.setOnClickListener(v -> {
            try {
                Shell.getShell().loadInputStream(callback, callback,
                        getResources().openRawResource(R.raw.info));
            } catch (NoShellException e) {
                e.printStackTrace();
            }
        });

        // Load a script from raw resources
        async_script.setOnClickListener(v -> {
            try {
                Shell.getShell().loadInputStreamAsync(callback, callback,
                        getResources().openRawResource(R.raw.count));
            } catch (NoShellException e) {
                e.printStackTrace();
            }
        });

        clear.setOnClickListener(v -> {
            callback.clear();
            console.setText("");
        });

        // We create a ShellCallback to update the UI with the Shell output
        callback = new CallbackList<String>() {
            StringBuilder builder = new StringBuilder();

            @Override
            public void onAddElement(String s) {
                builder.append(s).append('\n');
                console.setText(builder);
                sv.postDelayed(() -> sv.fullScroll(ScrollView.FOCUS_DOWN), 10);
            }

            @Override
            public void clear() {
                builder = new StringBuilder();
            }
        };
    }
}
