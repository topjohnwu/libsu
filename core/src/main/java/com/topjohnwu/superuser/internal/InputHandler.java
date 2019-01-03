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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface InputHandler {
    String TAG = "SHELL_IN";
    String IMPLTAG = "SHELLIMPL";

    void handleInput(OutputStream in) throws IOException;

    static InputHandler newInstance(String... cmd) {
        return in -> {
            InternalUtils.log(IMPLTAG, "CommandHandler");
            for (String command : cmd) {
                in.write(command.getBytes("UTF-8"));
                in.write('\n');
                in.flush();
                InternalUtils.log(TAG, command);
            }
        };
    }

    static InputHandler newInstance(InputStream is) {
        return in -> {
            InternalUtils.log(IMPLTAG, "InputStreamHandler");
            ShellUtils.pump(is, in);
            is.close();
            // Make sure it flushes the shell
            in.write('\n');
            in.flush();
        };
    }
}
