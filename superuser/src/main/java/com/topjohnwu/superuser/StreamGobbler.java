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
    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int TERMINATE = 2;

    private final InputStream in;
    private CharSequence token;
    private Collection<String> writer;

    private int status;

    StreamGobbler(InputStream in, CharSequence token) {
        status = PENDING;
        this.in = in;
        this.token = token;
    }

    @Override
    protected void finalize() throws Throwable {
        terminate();
    }

    synchronized void begin(Collection<String> out) {
        if (!isAlive())
            start();
        status = RUNNING;
        writer = out == null ? null : Collections.synchronizedCollection(out);
        notifyAll();
    }

    synchronized void terminate() {
        status = TERMINATE;
        if (isAlive())
            notifyAll();
    }

    private synchronized void notifyDone() {
        status = PENDING;
        writer = null;
        notifyAll();
    }

    synchronized void waitDone() throws InterruptedException {
        while (status != PENDING)
            wait();
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized(this) {
                    while (status != RUNNING) {
                        if (status == TERMINATE)
                            return;
                        wait();
                    }
                }
                synchronized (in) {
                    Utils.cleanInputStream(in);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (TextUtils.equals(line, token)) {
                            notifyDone();
                            break;
                        }
                        if (writer != null)
                            writer.add(line);
                        Utils.log(TAG, line);
                    }
                }
            } catch (InterruptedException | IOException ignored) {}
        }
    }
}
