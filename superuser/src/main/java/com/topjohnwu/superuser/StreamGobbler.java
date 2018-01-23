package com.topjohnwu.superuser;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

/**
 * Created by topjohnwu on 2018/1/19.
 */

class StreamGobbler extends Thread {

    private static final String TAG = "SHELLOUT";
    private static final int PENDING = 0;
    private static final int BEGIN = 1;
    private static final int RUNNING = 2;
    private static final int TERMINATE = 3;

    private final InputStream in;
    private CharSequence token;
    private List<String> writer;

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

    synchronized void begin(List<String> out) {
        if (!isAlive())
            start();
        status = BEGIN;
        synchronized (in) {
            Utils.cleanInputStream(in);
        }
        writer = out == null ? null : Collections.synchronizedList(out);
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
                    while (status != BEGIN) {
                        if (status == TERMINATE)
                            return;
                        wait();
                    }
                    status = RUNNING;
                }
                synchronized (in) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (TextUtils.equals(line, token))
                            break;
                        if (writer != null)
                            writer.add(line);
                        Utils.log(TAG, line);
                    }
                }
                notifyDone();
            } catch (InterruptedException | IOException ignored) {}
        }
    }
}
