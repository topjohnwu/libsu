package com.topjohnwu.superuser;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by topjohnwu on 2018/1/19.
 */

class StreamGobbler extends Thread {

    private static final String TAG = "SHELLOUT";

    BufferedReader reader;
    Collection<String> writer;
    CharSequence token;

    StreamGobbler(InputStream in, Collection<String> out, CharSequence token) {
        // Make sure our input is clean before running
        try {
            while (in.available() != 0)
                in.skip(in.available());
        } catch (IOException ignored) {}

        reader = new BufferedReader(new InputStreamReader(in));
        writer = out == null ? null : Collections.synchronizedCollection(out);
        this.token = token;
    }

    @Override
    public void run() {
        // Keep reading the InputStream until it ends (or an error occurs)
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (TextUtils.equals(line, token))
                    return;
                if (writer != null)
                    writer.add(line);
                Utils.log(TAG, line);
            }
        } catch (IOException ignored) {}
    }
}
