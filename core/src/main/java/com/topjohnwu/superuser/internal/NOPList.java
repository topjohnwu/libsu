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

import java.util.AbstractList;

public class NOPList extends AbstractList<String> {

    private static NOPList list;

    public static NOPList getInstance() {
        if (list == null)
            list = new NOPList();
        return list;
    }

    private NOPList() {
        super();
    }

    @Override
    public String get(int i) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public String set(int index, String element) {
        return null;
    }

    @Override
    public void add(int index, String element) {}

    @Override
    public String remove(int index) {
        return null;
    }
}
