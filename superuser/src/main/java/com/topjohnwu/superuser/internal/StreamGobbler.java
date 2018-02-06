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

import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

class StreamGobbler extends Thread {

    private static final String TAG = "SHELLOUT";
    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int TERMINATE = 2;

    private final InputStream in;
    private String token;
    private List<String> writer;

    private int status;

    public StreamGobbler(InputStream in, String token) {
        status = PENDING;
        this.in = in;
        this.token = token;
    }

    synchronized void begin(List<String> out) {
        if (!isAlive())
            start();
        status = RUNNING;
        ShellUtils.cleanInputStream(in);
        writer = out == null ? null : Collections.synchronizedList(out);
        notifyAll();
    }

    synchronized void waitDone() throws InterruptedException {
        while (status == RUNNING)
            wait();
    }

    private void output(String s) {
        if (writer != null)
            writer.add(s);
        InternalUtils.log(TAG, s);
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized(this) {
                    while (status != RUNNING) {
                        if (status == TERMINATE) {
                            notifyAll();
                            return;
                        }
                        wait();
                    }
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    int end = line.lastIndexOf(token);
                    if (end >= 0) {
                        if (end > 0)
                            output(line.substring(0, end));
                        break;
                    }
                    output(line);
                }
                reader.close();
                synchronized (this) {
                    status = PENDING;
                    notifyAll();
                }
            } catch (InterruptedException | IOException e) {
                status = TERMINATE;
            }
        }
    }
}
