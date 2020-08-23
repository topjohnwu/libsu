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

import static com.topjohnwu.superuser.internal.Utils.UTF_8;

abstract class StreamGobbler<T> implements Callable<T> {

    private static final String TAG = "SHELLOUT";

    private final String eos;
    private final int eosLength;

    protected InputStream in;
    protected List<String> list;

    StreamGobbler(String eos) {
        this.eos = eos;
        this.eosLength = this.eos.length();
    }

    public Callable<T> set(InputStream in, List<String> list) {
        this.in = in;
        this.list = list;
        return this;
    }

    protected boolean isEOS(String line) {
        if (line == null) {
            return true;
        }
        boolean eof = line.endsWith(eos);
        if (eof) {
            if (line.length() > eosLength) {
                line = line.substring(0, eosLength);
            } else {
                line = null;
            }
        }
        if (list != null && line != null) {
            list.add(line);
            Utils.log(TAG, line);
        }
        return eof;
    }

    static class OUT extends StreamGobbler<Integer> {

        private static final int NO_RESULT_CODE = 1;

        OUT(String eos) {
            super(eos);
        }

        @Override
        public Integer call() throws Exception {
            int code;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8))) {
                String line;
                do {
                    line = br.readLine();
                } while (!isEOS(line));
                String resultCodeLine = br.readLine();
                code = resultCodeLine == null ? NO_RESULT_CODE : Integer.parseInt(resultCodeLine);
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
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8))) {
                String line;
                do {
                    line = br.readLine();
                } while (!isEOS(line));
            }
            in = null;
            list = null;
            return null;
        }
    }
}
