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

import static com.topjohnwu.superuser.internal.FifoOutputStream.END_CMD;
import static com.topjohnwu.superuser.internal.FifoOutputStream.FIFO_TIMEOUT;
import static com.topjohnwu.superuser.internal.FifoOutputStream.TAG;
import static com.topjohnwu.superuser.internal.IOFactory.JUNK;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class FifoInputStream extends FilterInputStream {

    FifoInputStream(SuFile file) throws FileNotFoundException {
        super(null);
        if (file.isDirectory() || !file.canRead())
            throw new FileNotFoundException("No such file or directory: " + file.getAbsolutePath());

        File fifo = null;
        try {
            fifo = FileUtils.createTempFIFO();
            openStream(file, fifo);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException)
                throw (FileNotFoundException) cause;
            throw (FileNotFoundException) new FileNotFoundException("Cannot open fifo").initCause(e);
        } finally {
            if (fifo != null)
                fifo.delete();
        }
    }

    private void openStream(SuFile file, File fifo)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        file.getShell().execTask((in, out, err) -> {
            String cmd = "cat " + file.getEscapedPath() + " > " + fifo + " 2>/dev/null &";
            Utils.log(TAG, cmd);
            in.write(cmd.getBytes(UTF_8));
            in.write('\n');
            in.flush();
            in.write(END_CMD);
            in.flush();
            // Wait till the operation is done
            out.read(JUNK);
        });

        // Open the fifo only after the shell request
        Future<InputStream> stream = Shell.EXECUTOR.submit(() -> new FileInputStream(fifo));
        in = stream.get(FIFO_TIMEOUT, TimeUnit.MILLISECONDS);
    }
}
