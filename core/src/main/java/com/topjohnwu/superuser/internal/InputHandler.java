/*
 * Copyright 2020 John "topjohnwu" Wu
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.topjohnwu.superuser.internal.Utils.UTF_8;

interface InputHandler {

    String TAG = "SHELL_IN";

    void handleInput(OutputStream in) throws IOException;

    static InputHandler newInstance(String... cmd) {
        return in -> {
            for (String command : cmd) {
                in.write(command.getBytes(UTF_8));
                in.write('\n');
                Utils.log(TAG, command);
            }
        };
    }

    static InputHandler newInstance(InputStream is) {
        return in -> {
            Utils.log(TAG, "<InputStream>");
            Utils.pump(is, in);
            is.close();
            // Make sure it ends properly
            in.write('\n');
        };
    }
}
