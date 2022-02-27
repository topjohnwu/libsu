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

import static com.topjohnwu.superuser.internal.IOFactory.JUNK;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class FifoOutputStream extends FilterOutputStream {

    // Set a reasonable timeout here. It is possible that `cat` failed, and the
    // FIFO cannot be opened on our side without blocking forever.
    static final int FIFO_TIMEOUT = 250;
    static final String TAG = "FIFOIO";
    static final byte[] END_CMD = "echo\n".getBytes(UTF_8);

    private final File fifo;
    private boolean append;

    FifoOutputStream(SuFile file, boolean append) throws FileNotFoundException {
        super(null);
        this.append = append;
        if (!checkFile(file))
            throw new FileNotFoundException("No such file or directory");

        Context c = Utils.getDeContext(Utils.getContext());
        fifo = new File(c.getCacheDir(), UUID.randomUUID().toString());
        try {
            Os.mkfifo(fifo.getPath(), 0600);
        } catch (ErrnoException e) {
            throw (FileNotFoundException)
                    new FileNotFoundException("Failed to mkfifo").initCause(e);
        }
        // In case the stream was not manually closed
        fifo.deleteOnExit();

        try {
            openStream(file);
        } catch (Exception e){
            fifo.delete();
            throw e;
        }
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

    private void openStream(SuFile file) throws FileNotFoundException {
        try {
            file.getShell().execTask((in, out, err) -> {
                String cmd = "cat " + fifo + op() + file.getEscapedPath() + " 2>/dev/null &";
                Utils.log(TAG, cmd);
                in.write(cmd.getBytes(UTF_8));
                in.write('\n');
                in.flush();
                in.write(END_CMD);
                in.flush();
                // Wait till the operation is done
                out.read(JUNK);
            });
        } catch (IOException e) {
            throw (FileNotFoundException)
                    new FileNotFoundException("Error running root command").initCause(e);
        }

        // Open the fifo only after the shell request
        Future<OutputStream> stream = Shell.EXECUTOR.submit(() -> new FileOutputStream(fifo));
        try {
            out = stream.get(FIFO_TIMEOUT, TimeUnit.MILLISECONDS);
            // Root command might fail for any random reason, bail out
        } catch (ExecutionException |InterruptedException| TimeoutException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException)
                throw (FileNotFoundException) cause;
            else
                throw (FileNotFoundException)
                        new FileNotFoundException("Cannot open fifo").initCause(e);
        }
    }

    private String op() {
        return append ? " >> " : " > ";
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        fifo.delete();
    }
}
