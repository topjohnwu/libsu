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

import com.topjohnwu.superuser.io.SuFile;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

abstract class BaseSuInputStream extends FilterInputStream {

    BaseSuInputStream(SuFile file) throws FileNotFoundException {
        super(null);
        if (file.isDirectory() || !file.canRead())
            throw new FileNotFoundException("No such file or directory");
    }
}

abstract class BaseSuOutputStream extends FilterOutputStream {

    protected boolean append;

    BaseSuOutputStream(SuFile file, boolean append) throws FileNotFoundException {
        super(null);
        this.append = append;
        if (!checkFile(file))
            throw new FileNotFoundException("No such file or directory");
    }

    private boolean checkFile(SuFile file) {
        if (file.isDirectory())
            return false;
        if (file.isBlock() || file.isCharacter()) {
            append = false;
            return true;
        }
        if (append)
            return file.canWrite() || file.createNewFile();
        return file.clear();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    protected final String op() {
        return append ? " >> " : " > ";
    }
}
