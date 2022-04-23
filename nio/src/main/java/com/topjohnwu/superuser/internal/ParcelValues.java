/*
 * Copyright 2022 John "topjohnwu" Wu
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

import java.util.ArrayList;

class ParcelValues extends ArrayList<Object> implements Parcelable {

    private static final ClassLoader cl = ParcelValues.class.getClassLoader();

    static final Creator<ParcelValues> CREATOR = new Creator<ParcelValues>() {
        @Override
        public ParcelValues createFromParcel(Parcel in) {
            return new ParcelValues(in);
        }

        @Override
        public ParcelValues[] newArray(int size) {
            return new ParcelValues[size];
        }
    };

    ParcelValues() {}

    private ParcelValues(Parcel in) {
        int size = in.readInt();
        ensureCapacity(size);
        for (int i = 0; i < size; ++i) {
            add(in.readValue(cl));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getTyped(int index) {
        return (T) get(index);
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(size());
        for (Object o : this) {
            dest.writeValue(o);
        }
    }
}
