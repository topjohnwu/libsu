/*
 * Copyright 2018 John "topjohnwu" Wu
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

package com.topjohnwu.superuser.io;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.Factory;
import com.topjohnwu.superuser.internal.ShellFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

/**
 * A {@link File} implementation with root access.
 * <p>
 * Without root access in the global shell, this class is simply just a wrapper around {@link File}.
 * However, when root is available, all methods will be backed by commands executing via the
 * global root shell.
 * <p>
 * This class has the exact same behavior as a normal {@link File}, however non of the operations
 * are atomic if backed with shell commands. This is a limitation of using shells, be aware of it.
 * <p>
 * The {@code Shell} instance will be acquired via {@link Shell#getShell()}.
 * The methods of this class require: {@code rm}, {@code rmdir}, {@code readlink}, {@code mv},
 * {@code ls}, {@code mkdir}, {@code touch}, or optionally for better support,
 * {@code blockdev} and {@code stat}.
 * All required tools are available on modern Android versions (tested on Lollipop+),
 * older versions might need to install {@code busybox} to make things work properly.
 * Some operations could have oddities without busybox due to the very limited tools available,
 * check the method descriptions for more info before using it.
 * @see com.topjohnwu.superuser.BusyBox
 */
public class SuFile extends File {
    private File f;

    /**
     * Create a new {@code SuFile} using the path of the given {@code File}.
     * @param file the base file.
     */
    public SuFile(@NonNull File file) {
        super("");
        if (file instanceof SuFile) {
            f = ((SuFile) file).f;
        } else if (file instanceof ShellFile || !Shell.rootAccess()) {
            f = file;
        } else {
            f = Factory.createShellFile(file);
        }
    }

    public SuFile(@NonNull String pathname) {
        this(new File(pathname));
    }

    public SuFile(String parent, @NonNull String child) {
        this(new File(parent, child));
    }

    public SuFile(File parent, @NonNull String child) {
        this(new File(parent, child));
    }

    public SuFile(@NonNull URI uri) {
        this(new File(uri));
    }

    /**
     * @deprecated
     * Create a new {@code SuFile} using the path of the given {@code File}.
     * @param file the base file.
     * @param shell whether use shell for operations.
     */
    @Deprecated
    public SuFile(@NonNull File file, boolean shell) {
        this(file);
    }

    /**
     * @deprecated
     * Create a new {@code SuFile} using a path.
     * @param pathname the path to the file.
     * @param shell whether use shell for operations.
     */
    @Deprecated
    public SuFile(@NonNull String pathname, boolean shell) {
        this(pathname);
    }

    boolean isSU() {
        return f instanceof ShellFile;
    }

    ShellFile getShellFile() {
        return (ShellFile) f;
    }

    @NonNull
    @Override
    public String getName() {
        return f.getName();
    }

    @Override
    public String getParent() {
        return f.getParent();
    }

    @Override
    public SuFile getParentFile() {
        return new SuFile(f.getParentFile());
    }

    @NonNull
    @Override
    public String getPath() {
        return f.getPath();
    }

    @Override
    public boolean isAbsolute() {
        return f.isAbsolute();
    }

    @NonNull
    @Override
    public String getAbsolutePath() {
        return f.getAbsolutePath();
    }

    @NonNull
    @Override
    public SuFile getAbsoluteFile() {
        return new SuFile(f.getAbsoluteFile());
    }

    @NonNull
    @Override
    public String getCanonicalPath() throws IOException {
        return f.getCanonicalPath();
    }

    @NonNull
    @Override
    public SuFile getCanonicalFile() throws IOException {
        return new SuFile(f.getCanonicalFile());
    }

    @Override
    @Deprecated
    public URL toURL() throws MalformedURLException {
        return f.toURL();
    }

    @NonNull
    @Override
    public URI toURI() {
        return f.toURI();
    }

    @Override
    public boolean canRead() {
        return f.canRead();
    }

    @Override
    public boolean canWrite() {
        return f.canWrite();
    }

    @Override
    public boolean exists() {
        return f.exists();
    }

    @Override
    public boolean isDirectory() {
        return f.isDirectory();
    }

    @Override
    public boolean isFile() {
        return f.isFile();
    }

    @Override
    public boolean isHidden() {
        return f.isHidden();
    }

    @Override
    public long lastModified() {
        return f.lastModified();
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * <p>
     * Note: If there is no {@code blockdev} and {@code stat} in {@code PATH}, the file size is
     * the value reported from {@code ls -ld}, which will not correctly report the size of block files.
     * @return the size in bytes of the underlying file.
     * @see File#length()
     */
    @Override
    public long length() {
        return f.length();
    }

    @Override
    public boolean createNewFile() {
        try {
            return f.createNewFile();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean delete() {
        return f.delete();
    }

    @Override
    public void deleteOnExit() {
        f.deleteOnExit();
    }

    @Override
    public String[] list() {
        return f.list();
    }

    @Override
    public String[] list(FilenameFilter filter) {
        return f.list(filter);
    }

    private SuFile[] trans(File[] list) {
        SuFile ret[] = new SuFile[list.length];
        for (int i = 0; i < list.length; ++i)
            ret[i] = new SuFile(list[i]);
        return ret;
    }

    @Override
    public SuFile[] listFiles() {
        return trans(f.listFiles());
    }

    @Override
    public SuFile[] listFiles(FilenameFilter filter) {
        return trans(f.listFiles(filter));
    }

    @Override
    public SuFile[] listFiles(FileFilter filter) {
        return trans(f.listFiles(filter));
    }

    @Override
    public boolean mkdir() {
        return f.mkdir();
    }

    @Override
    public boolean mkdirs() {
        return f.mkdirs();
    }

    @Override
    public boolean renameTo(File dest) {
        return f.renameTo(dest);
    }

    /**
     * Sets the last-modified time of the file or directory named by this abstract pathname.
     * <p>
     * Note: On older Android devices, the {@code touch} commands accepts a different timestamp
     * format than GNU {@code touch}. This shell implementation uses the format accepted in GNU
     * coreutils, which is the same in more recent Android versions and busybox, so the operation
     * might fail on older Android versions.
     * @param time The new last-modified time, measured in milliseconds since the epoch.
     * @return {@code true} if and only if the operation succeeded; {@code false} otherwise.
     */
    @Override
    public boolean setLastModified(long time) {
        return f.setLastModified(time);
    }

    @Override
    public boolean setReadOnly() {
        return f.setReadOnly();
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return f.setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setWritable(boolean writable) {
        return f.setWritable(writable);
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return f.setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setReadable(boolean readable) {
        return f.setReadable(readable);
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return f.setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean setExecutable(boolean executable) {
        return f.setExecutable(executable);
    }

    @Override
    public boolean canExecute() {
        return f.canExecute();
    }

    @Override
    public long getTotalSpace() {
        return f.getTotalSpace();
    }

    @Override
    public long getFreeSpace() {
        return f.getFreeSpace();
    }

    @Override
    public long getUsableSpace() {
        return f.getUsableSpace();
    }

    @Override
    public int compareTo(File pathname) {
        return f.compareTo(pathname);
    }

    @Override
    public boolean equals(Object obj) {
        return f.equals(obj);
    }

    @Override
    public int hashCode() {
        return f.hashCode();
    }

    @Override
    public String toString() {
        return f.toString();
    }

    @NonNull
    @Override
    @RequiresApi(Build.VERSION_CODES.O)
    public Path toPath() {
        return f.toPath();
    }
}
