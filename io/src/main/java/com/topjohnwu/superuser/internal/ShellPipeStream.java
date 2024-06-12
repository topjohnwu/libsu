/*
 * Copyright 2024 John "topjohnwu" Wu
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

import static com.topjohnwu.superuser.internal.IOFactory.JUNK;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

class ShellPipeStream {

    private static final int FIFO_TIMEOUT = 250;
    private static final String TAG = "FIFOIO";
    private static final byte[] END_CMD = "echo\n".getBytes(UTF_8);

    static InputStream openReadStream(SuFile file) throws FileNotFoundException {
        if (file.isDirectory() || !file.canRead())
            throw new FileNotFoundException("No such file or directory: " + file.getPath());

        File f = null;
        try {
            File fifo = FileUtils.createTempFIFO();
            f = fifo;
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
            FutureTask<InputStream> stream = new FutureTask<>(() -> new FileInputStream(fifo));
            Shell.EXECUTOR.execute(stream);
            return stream.get(FIFO_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException)
                throw (FileNotFoundException) cause;
            Throwable err = new FileNotFoundException("Cannot open fifo").initCause(e);
            throw (FileNotFoundException) err;
        } finally {
            if (f != null)
                f.delete();
        }
    }

    static OutputStream openWriteStream(SuFile file, boolean append) throws FileNotFoundException {
        if (file.isDirectory())
            throw new FileNotFoundException(file.getPath() + " is not a file but a directory");
        if (file.isBlock() || file.isCharacter()) {
            append = false;
        }
        if (append && !file.canWrite() && !file.createNewFile()) {
            throw new FileNotFoundException("Cannot write to file " + file.getPath());
        } else if (!file.clear()) {
            throw new FileNotFoundException("Failed to clear file " + file.getPath());
        }

        String op = append ? " >> " : " > ";
        File f = null;
        try {
            File fifo = FileUtils.createTempFIFO();
            f = fifo;
            file.getShell().execTask((in, out, err) -> {
                String cmd = "cat " + fifo + op + file.getEscapedPath() + " 2>/dev/null &";
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
            FutureTask<OutputStream> stream = new FutureTask<>(() -> new FileOutputStream(fifo));
            Shell.EXECUTOR.execute(stream);
            return stream.get(FIFO_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException)
                throw (FileNotFoundException) cause;
            Throwable err = new FileNotFoundException("Cannot open fifo").initCause(e);
            throw (FileNotFoundException) err;
        } finally {
            if (f != null)
                f.delete();
        }
    }
}
