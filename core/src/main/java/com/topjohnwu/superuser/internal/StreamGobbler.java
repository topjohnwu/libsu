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
import java.util.List;
import java.util.concurrent.Callable;

abstract class StreamGobbler<T> implements Callable<T> {

    private static final String TAG = "SHELLOUT";

    private final String eos;

    protected InputStream in;
    protected List<String> list;

    StreamGobbler(String eos) {
        this.eos = eos;
    }

    public Callable<T> set(InputStream in, List<String> list) {
        this.in = in;
        this.list = list;
        return this;
    }

    protected boolean isEOS(String line) {
        boolean eof = false;
        int sl = line.length() - 1;
        int tl = eos.length() - 1;
        if (sl >= tl) {
            eof = true;
            for (; tl >= 0; --tl, --sl) {
                if (eos.charAt(tl) != line.charAt(sl)) {
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

    static class OUT extends StreamGobbler<Integer> {

        OUT(String eos) {
            super(eos);
        }

        @Override
        public Integer call() throws Exception {
            int code;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                for (;;) {
                    if (isEOS(br.readLine()))
                        break;
                }
                code = Integer.parseInt(br.readLine());
            }
            in = null;
            list = null;
            return code;
        }
    }

    static class ERR extends StreamGobbler<Void> {
        ERR(String eos) {
            super(eos);
        }

        @Override
        public Void call() throws Exception {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                for (;;) {
                    if (isEOS(br.readLine()))
                        break;
                }
            }
            in = null;
            list = null;
            return null;
        }
    }
}
