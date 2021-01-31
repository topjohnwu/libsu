/*
 * Copyright 2021 John "topjohnwu" Wu
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Callable;

import static com.topjohnwu.superuser.internal.TaskImpl.END_UUID;
import static com.topjohnwu.superuser.internal.TaskImpl.UUID_LEN;
import static com.topjohnwu.superuser.internal.Utils.UTF_8;

abstract class StreamGobbler<T> implements Callable<T> {

    private static final String TAG = "SHELLOUT";

    protected final InputStream in;
    protected final List<String> list;

    StreamGobbler(InputStream in, List<String> list) {
        this.in = in;
        this.list = list;
    }

    private boolean outputAndCheck(String line) {
        if (line == null)
            return false;

        int len = line.length();
        boolean end = line.startsWith(END_UUID, len - UUID_LEN);
        if (end) {
            if (len == UUID_LEN)
                return false;
            line = line.substring(0, len - UUID_LEN);
        }
        if (list != null) {
            list.add(line);
            Utils.log(TAG, line);
        }
        return !end;
    }

    protected String process(boolean res) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
        String line;
        do {
            line = br.readLine();
        } while (outputAndCheck(line));
        return res ? br.readLine() : null;
    }

    static class OUT extends StreamGobbler<Integer> {

        private static final int NO_RESULT_CODE = 1;

        OUT(InputStream in, List<String> list) { super(in, list); }

        @Override
        public Integer call() throws Exception {
            String code = process(true);
            try {
                return code == null ? NO_RESULT_CODE : Integer.parseInt(code);
            } catch (NumberFormatException e) {
                return NO_RESULT_CODE;
            }
        }
    }

    static class ERR extends StreamGobbler<Void> {

        ERR(InputStream in, List<String> list) { super(in, list); }

        @Override
        public Void call() throws Exception {
            process(false);
            return null;
        }
    }
}
