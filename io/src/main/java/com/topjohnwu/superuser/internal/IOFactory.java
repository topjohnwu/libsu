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

import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileNotFoundException;

public final class IOFactory {
    private IOFactory() {}

    public static ShellIO createShellIO(SuFile file, String mode) throws FileNotFoundException {
        String internalMode;
        switch (mode) {
            case "r":
                internalMode = "r";
                break;
            case "rw":
            case "rws":
            case "rwd":
                internalMode = "rw";
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }
        return ShellIO.get(file, internalMode);
    }

    public static RAFWrapper createRAFWrapper(File file, String mode)
            throws FileNotFoundException {
        return new RAFWrapper(file, mode);
    }

    public static ShellInputStream createShellInputStream(SuFile file) throws FileNotFoundException {
        return new ShellInputStream(file);
    }

    public static ShellOutputStream createShellOutputStream(SuFile file, boolean append)
            throws FileNotFoundException {
        return new ShellOutputStream(file, append);
    }
}
