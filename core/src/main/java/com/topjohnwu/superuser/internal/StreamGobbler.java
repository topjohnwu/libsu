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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

class StreamGobbler implements Callable<Integer> {

    private static final String TAG = "SHELLOUT";

    private final String delim;
    private final boolean returnCode;

    private InputStream in;
    private List<String> list;

    StreamGobbler(String delim, Boolean b) {
        this.delim = delim;
        returnCode = b;
    }

    public Callable<Integer> set(InputStream in, List<String> list) {
        this.in = in;
        this.list = list == null ? null : Collections.synchronizedList(list);
        return this;
    }

    private boolean output(String line) {
        boolean eof = false;
        int sl = line.length() - 1;
        int tl = delim.length() - 1;
        if (sl >= tl) {
            eof = true;
            for (; tl >= 0; --tl, --sl) {
                if (delim.charAt(tl) != line.charAt(sl)) {
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

    @Override
    public Integer call() throws Exception {
        int code = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            for (;;) {
                if (output(br.readLine()))
                    break;
            }
            if (returnCode)
                code = Integer.parseInt(br.readLine());
        }
        in = null;
        list = null;
        return code;
    }
}
