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

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

class IOResult implements Parcelable {

    private static final String REMOTE_ERR_MSG = "Exception thrown on remote process";
    private static final ClassLoader cl = IOResult.class.getClassLoader();

    private final Object val;

    IOResult() {
        val = null;
    }

    IOResult(Object v) {
        val = v;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(val);
    }

    void checkException() throws IOException {
        if (val instanceof Throwable)
            throw new IOException(REMOTE_ERR_MSG, (Throwable) val);
    }

    @SuppressWarnings("unchecked")
    <T> T tryAndGet() throws IOException {
        checkException();
        return (T) val;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private IOResult(Parcel in) {
        val = in.readValue(cl);
    }

    static final Creator<IOResult> CREATOR = new Creator<IOResult>() {
        @Override
        public IOResult createFromParcel(Parcel in) {
            return new IOResult(in);
        }

        @Override
        public IOResult[] newArray(int size) {
            return new IOResult[size];
        }
    };
}
