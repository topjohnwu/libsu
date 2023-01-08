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

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.IOException;

class ByteOutputStream extends ByteArrayOutputStream {
    // Expose internal buffer
    public byte[] getBuf() {
        return buf;
    }

    public void writeTo(@NonNull DataOutput out) throws IOException {
        out.write(buf, 0, count);
    }
}
