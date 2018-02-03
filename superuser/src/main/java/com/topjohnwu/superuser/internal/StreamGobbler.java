/*
 * Copyright 2018 John "topjohnwu" Wu
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

package com.topjohnwu.superuser.internal;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

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

    public StreamGobbler(InputStream in, CharSequence token) {
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
            LibUtils.cleanInputStream(in);
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
                        LibUtils.log(TAG, line);
                    }
                }
                notifyDone();
            } catch (InterruptedException | IOException ignored) {}
        }
    }
}
