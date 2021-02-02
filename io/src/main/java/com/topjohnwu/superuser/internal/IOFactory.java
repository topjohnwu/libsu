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

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileNotFoundException;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class IOFactory {

    static final byte[] JUNK = new byte[1];

    private IOFactory() {}

    public static ShellIO shellIO(SuFile file, String mode) throws FileNotFoundException {
        switch (mode) {
            case "r":
                break;
            case "rw":
            case "rws":
            case "rwd":
                mode = "rw";
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }
        return ShellIO.get(file, mode);
    }

    public static RAFWrapper raf(File file, String mode) throws FileNotFoundException {
        return new RAFWrapper(file, mode);
    }

    public static ShellInputStream shellIn(SuFile file) throws FileNotFoundException {
        return new ShellInputStream(file);
    }

    @RequiresApi(21)
    public static FifoInputStream fifoIn(SuFile file) throws FileNotFoundException {
        return new FifoInputStream(file);
    }

    public static CopyInputStream copyIn(SuFile file) throws FileNotFoundException {
        return new CopyInputStream(file);
    }

    public static ShellOutputStream shellOut(SuFile file, boolean append)
            throws FileNotFoundException {
        return new ShellOutputStream(file, append);
    }

    @RequiresApi(21)
    public static FifoOutputStream fifoOut(SuFile file, boolean append)
            throws FileNotFoundException {
        return new FifoOutputStream(file, append);
    }

    public static CopyOutputStream copyOut(SuFile file, boolean append)
            throws FileNotFoundException {
        return new CopyOutputStream(file, append);
    }
}
