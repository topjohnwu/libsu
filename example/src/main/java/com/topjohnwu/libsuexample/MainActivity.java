package com.topjohnwu.libsuexample;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.topjohnwu.superuser.NoShellException;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellCallback;
import com.topjohnwu.superuser.ShellCallbackVector;

import java.util.List;

public class MainActivity extends Activity {

    private TextView console;
    private Button cmd, script, clear;
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

        cmd = findViewById(R.id.run_cmd);
        script = findViewById(R.id.run_script);
        clear = findViewById(R.id.clear);

        // Run the shell command in the input box
        cmd.setOnClickListener(v -> {
           Shell.su(callback, input.getText().toString());
           input.setText("");
        });

        // Load a script from raw resources
        script.setOnClickListener(v -> {
            try {
                Shell.getShell().loadInputStream(callback, callback,
                        getResources().openRawResource(R.raw.script));
            } catch (NoShellException e) {
                e.printStackTrace();
            }
        });

        clear.setOnClickListener(v -> {
            callback.clear();
            console.setText("");
        });

        // We create a ShellCallback to update the UI with the Shell output
        callback = new ShellCallback() {
            StringBuilder builder = new StringBuilder();
            @Override
            public void onShellOutput(String e) {
                builder.append(e).append('\n');
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
