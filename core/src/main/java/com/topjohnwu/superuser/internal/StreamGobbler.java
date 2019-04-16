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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

class StreamGobbler implements Callable<Integer> {

    private static final String TAG = "SHELLOUT";

    private final String token;
    private final boolean returnCode;

    private InputStream in;
    private List<String> list;

    StreamGobbler(String token, Boolean b) {
        this.token = token;
        returnCode = b;
    }

    public Callable<Integer> set(InputStream in, List<String> list) {
        this.in = in;
        this.list = list == null ? null : Collections.synchronizedList(list);
        return this;
    }

    private void output(String s) {
        if (list != null) {
            list.add(s);
            InternalUtils.log(TAG, s);
        }
    }

    @Override
    public Integer call() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null) {
            int end = line.lastIndexOf(token);
            if (end >= 0) {
                if (end > 0) {
                    while (line.charAt(end - 1) == 0)
                        --end;
                    output(line.substring(0, end));
                }
                break;
            }
            output(line);
        }
        int code = returnCode ? Integer.parseInt(reader.readLine()) : 0;
        reader.close();
        in = null;
        list = null;
        return code;
    }
}
