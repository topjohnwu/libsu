/*
 * Copyright 2019 John "topjohnwu" Wu
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
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

class StreamGobbler implements Callable<Integer> {

    private static final String TAG = "SHELLOUT";

    private final String token;
    private final boolean returnCode;
    private final StringBuilder sb;

    private InputStream in;
    private List<String> list;

    StreamGobbler(String token, Boolean b) {
        this.token = token;
        returnCode = b;
        sb = new StringBuilder();
    }

    public Callable<Integer> set(InputStream in, List<String> list) {
        this.in = in;
        this.list = list == null ? null : Collections.synchronizedList(list);
        return this;
    }

    private boolean output(String line) {
        boolean eof = false;
        int sl = line.length() - 1;
        int tl = token.length() - 1;
        if (sl >= tl) {
            eof = true;
            for (; tl >= 0; --tl, --sl) {
                if (token.charAt(tl) != line.charAt(sl)) {
                    eof = false;
                    break;
                }
            }
            if (eof)
                line = sl >= 0 ? line.substring(0, sl + 1) : null;
        }
        if (list != null && line != null) {
            list.add(line);
            InternalUtils.log(TAG, line);
        }
        return eof;
    }

    private String readLine(InputStreamReader reader) throws IOException {
        sb.setLength(0);
        for (int c;;) {
            c = reader.read();
            if (c == '\n' || c == -1)
                break;
            sb.append((char) c);
        }
        return sb.toString();
    }

    @Override
    public Integer call() throws Exception {
        InputStreamReader reader = new InputStreamReader(in, "UTF-8");
        for (;;) {
            if (output(readLine(reader)))
                break;
        }
        int code = returnCode ? Integer.parseInt(readLine(reader)) : 0;
        reader.close();
        in = null;
        list = null;
        return code;
    }
}
