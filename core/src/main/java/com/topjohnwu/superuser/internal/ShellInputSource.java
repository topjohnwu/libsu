/*
 * Copyright 2023 John "topjohnwu" Wu
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface ShellInputSource extends Closeable {
    String TAG = "SHELL_IN";

    void serve(OutputStream out) throws IOException;

    @Override
    default void close() {}
}

class InputStreamSource implements ShellInputSource {

    private final InputStream in;
    InputStreamSource(InputStream in) { this.in = in; }

    @Override
    public void serve(OutputStream out) throws IOException {
        Utils.pump(in, out);
        in.close();
        out.write('\n');
        Utils.log(TAG, "<InputStream>");
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException ignored) {}
    }
}

class CommandSource implements ShellInputSource {

    private final String[] cmd;
    CommandSource(String[] cmd) { this.cmd = cmd; }

    @Override
    public void serve(OutputStream out) throws IOException {
        for (String command : cmd) {
            out.write(command.getBytes(UTF_8));
            out.write('\n');
            Utils.log(TAG, command);
        }
    }
}
