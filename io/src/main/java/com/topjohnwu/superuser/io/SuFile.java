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

package com.topjohnwu.superuser.io;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

/**
 * A {@link File} implementation using root shell.
 * <p>
 * All methods of this class are implemented by executing commands via the main shell.
 * <p>
 * This class has the same behavior as a normal {@link File}, however none of the operations
 * are atomic. This is a limitation for using shell commands.
 * <p>
 * Each method description in this class will list out its required commands.
 * The following commands exists on all Android versions: {@code rm}, {@code rmdir},
 * {@code mv}, {@code ls}, and {@code mkdir}.
 * The following commands require {@code toybox} on Android 6.0 and higher, or {@code busybox}
 * to support legacy devices: {@code readlink}, {@code touch}, and {@code stat}.
 * <p>
 * This class has handy factory methods {@code SuFile.open(...)} for obtaining {@link File}
 * instances. These factory methods will return a normal {@link File} instance if the main
 * shell does not have root access, or else return a {@link SuFile} instance.
 */
public class SuFile extends File {

    private final String[] CMDs;

    public static File open(String pathname) {
        return Shell.rootAccess() ? new SuFile(pathname) : new File(pathname);
    }

    public static File open(String parent, String child) {
        return Shell.rootAccess() ? new SuFile(parent, child) : new File(parent, child);
    }

    public static File open(File parent, String child) {
        return Shell.rootAccess() ? new SuFile(parent, child) : new File(parent, child);
    }

    public static File open(URI uri) {
        return Shell.rootAccess() ? new SuFile(uri) : new File(uri);
    }

    SuFile(@NonNull File file) {
        super(file.getAbsolutePath());
        CMDs = new String[2];
        CMDs[0] = "__F_='" + file.getAbsolutePath() +  "'";
    }

    public SuFile(String pathname) {
        this(new File(pathname));
    }

    public SuFile(String parent, String child) {
        this(new File(parent, child));
    }

    public SuFile(File parent, String child) {
        this(parent.getAbsolutePath(), child);
    }

    public SuFile(URI uri) {
        this(new File(uri));
    }

    private String[] setCmd(String c) {
        CMDs[1] = c;
        return CMDs;
    }

    private String cmd(String c) {
        return ShellUtils.fastCmd(setCmd(c));
    }

    private boolean cmdBool(String c) {
        return ShellUtils.fastCmdResult(setCmd(c));
    }

    @Override
    public boolean canExecute() {
        return cmdBool("[ -x \"$__F_\" ]");
    }

    @Override
    public boolean canRead() {
        return cmdBool("[ -r \"$__F_\" ]");
    }

    @Override
    public boolean canWrite() {
        return cmdBool("[ -w \"$__F_\" ]");
    }

    @Override
    public boolean createNewFile() {
        return cmdBool("[ ! -e \"$__F_\" ] && echo -n > \"$__F_\"");
    }

    /**
     * Deletes the file or directory denoted by this abstract pathname. If
     * this pathname denotes a directory, then the directory must be empty in
     * order to be deleted.
     * <p>
     * Requires command {@code rm} for files, and {@code rmdir} for directories.
     * @see File#delete()
     */
    @Override
    public boolean delete() {
        return cmdBool("rm -f \"$__F_\" || rmdir -f \"$__F_\"");
    }

    /**
     * Deletes the file or directory denoted by this abstract pathname. If
     * this pathname denotes a directory, then the directory will be recursively
     * removed.
     * <p>
     * Requires command {@code rm}.
     * @see File#delete()
     */
    public boolean deleteRecursive() {
        return cmdBool("rm -rf \"$__F_\"");
    }

    /**
     * Clear the content of the file denoted by this abstract pathname.
     * Creates a new file if it does not already exist.
     * @return true if the operation succeeded
     */
    public boolean clear() {
        return cmdBool("echo -n > \"$__F_\"");
    }

    /**
     * Unsupported
     */
    @Override
    public void deleteOnExit() {
        throw new UnsupportedOperationException("Unsupported SuFile operation");
    }

    @Override
    public boolean exists() {
        return cmdBool("[ -e \"$__F_\" ]");
    }

    @NonNull
    @Override
    public SuFile getAbsoluteFile() {
        return this;
    }

    /**
     * Returns the canonical pathname string of this abstract pathname.
     * <p>
     * Requires command {@code readlink}.
     * @see File#getCanonicalPath()
     */
    @NonNull
    @Override
    public String getCanonicalPath() {
        String path = cmd("readlink -f \"$__F_\"");
        return path.isEmpty() ? getAbsolutePath() : path;
    }

    /**
     * Returns the canonical form of this abstract pathname.
     * <p>
     * Requires command {@code readlink}.
     * @see File#getCanonicalFile()
     */
    @NonNull
    @Override
    public SuFile getCanonicalFile() {
        return new SuFile(getCanonicalPath());
    }

    @Override
    public SuFile getParentFile() {
        String parent = getParent();
        return parent == null ? null : new SuFile(parent);
    }

    private long statFS(String fmt) {
        String[] res = cmd("stat -fc '%S " + fmt + "' \"$__F_\"").split(" ");
        if (res.length != 2)
            return Long.MAX_VALUE;
        try {
            return Long.parseLong(res[0]) * Long.parseLong(res[1]);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }


    /**
     * Returns the number of unallocated bytes in the partition.
     * <p>
     * Requires command {@code stat}.
     * @see File#getFreeSpace()
     */
    @Override
    public long getFreeSpace() {
        return statFS("%f");
    }

    /**
     * Returns the size of the partition.
     * <p>
     * Requires command {@code stat}.
     * @see File#getTotalSpace()
     */
    @Override
    public long getTotalSpace() {
        return statFS("%b");
    }

    /**
     * Returns the number of bytes available to this process on the partition.
     * <p>
     * Requires command {@code stat}.
     * @see File#getUsableSpace()
     */
    @Override
    public long getUsableSpace() {
        return statFS("%a");
    }

    @Override
    public boolean isDirectory() {
        return cmdBool("[ -d \"$__F_\" ]");
    }

    @Override
    public boolean isFile() {
        return cmdBool("[ -f \"$__F_\" ]");
    }

    /**
     * @return true if the abstract pathname denotes a block device.
     */
    public boolean isBlock() {
        return cmdBool("[ -b \"$__F_\" ]");
    }

    /**
     * @return true if the abstract pathname denotes a character device.
     */
    public boolean isCharacter() {
        return cmdBool("[ -c \"$__F_\" ]");
    }

    /**
     * @return true if the abstract pathname denotes a symbolic link file.
     */
    public boolean isSymlink() {
        return cmdBool("[ -L \"$__F_\" ]");
    }

    /**
     * Returns the time that the file denoted by this abstract pathname was
     * last modified.
     * <p>
     * Requires command {@code stat}.
     * @see File#lastModified()
     */
    @Override
    public long lastModified() {
        try {
            return Long.parseLong(cmd("stat -c '%Y' \"$__F_\"")) * 1000;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * <p>
     * Requires command {@code stat}.
     * @see File#length()
     */
    @Override
    public long length() {
        try {
            Long.parseLong(cmd("stat -c '%s' \"$__F_\""));
        } catch (NumberFormatException ignored) {}
        return 0L;
    }

    /**
     * Creates the directory named by this abstract pathname.
     * <p>
     * Requires command {@code mkdir}.
     * @see File#mkdir()
     */
    @Override
    public boolean mkdir() {
        return cmdBool("mkdir \"$__F_\"");
    }

    /**
     * Creates the directory named by this abstract pathname, including any
     * necessary but nonexistent parent directories.
     * <p>
     * Requires command {@code mkdir}.
     * @see File#mkdirs()
     */
    @Override
    public boolean mkdirs() {
        return cmdBool("mkdir -p \"$__F_\"");
    }

    /**
     * Renames the file denoted by this abstract pathname.
     * <p>
     * Requires command {@code mv}.
     * @see File#renameTo(File)
     */
    @Override
    public boolean renameTo(File dest) {
        return cmdBool("mv -f \"$__F_\" '" + dest.getAbsolutePath() + "'");
    }

    private boolean setPerms(boolean set, boolean ownerOnly, int b) {
        char[] perms = cmd("stat -c '%a' \"$__F_\"").toCharArray();
        if (perms.length != 3)
            return false;
        for (int i = 0; i < 3; ++i) {
            int perm = perms[i] - '0';
            if (set && (!ownerOnly || i == 0))
                perm |= b;
            else
                perm &= ~(b);
            perms[i] = (char) (perm + '0');
        }
        return cmdBool("chmod " + new String(perms) + " \"$__F_\"");
    }

    /**
     * Sets the owner's or everybody's execute permission for this abstract
     * pathname.
     * <p>
     * Requires command {@code stat} and {@code chmod}.
     * @see File#setExecutable(boolean, boolean)
     */
    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return setPerms(executable, ownerOnly, 0x1);
    }


    /**
     * Sets the owner's or everybody's read permission for this abstract
     * pathname.
     * <p>
     * Requires command {@code stat} and {@code chmod}.
     * @see File#setReadable(boolean, boolean)
     */
    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return setPerms(readable, ownerOnly, 0x4);
    }

    /**
     * Sets the owner's or everybody's write permission for this abstract
     * pathname.
     * <p>
     * Requires command {@code stat} and {@code chmod}.
     * @see File#setWritable(boolean, boolean)
     */
    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return setPerms(writable, ownerOnly, 0x2);
    }

    /**
     * Marks the file or directory named by this abstract pathname so that
     * only read operations are allowed.
     * <p>
     * Requires command {@code stat} and {@code chmod}.
     * @see File#setReadOnly()
     */
    @Override
    public boolean setReadOnly() {
        return setWritable(false, false) && setExecutable(false, false);
    }

    /**
     * Sets the last-modified time of the file or directory named by this abstract pathname.
     * <p>
     * Note: On Android 5.1 and lower, the {@code touch} command accepts a different timestamp
     * format than GNU {@code touch}. This implementation uses the format accepted in GNU
     * coreutils, which is the same format accepted by toybox and busybox, so this operation
     * may fail on older Android versions without busybox.
     * @param time The new last-modified time, measured in milliseconds since the epoch.
     * @return {@code true} if and only if the operation succeeded; {@code false} otherwise.
     */
    @Override
    public boolean setLastModified(long time) {
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
        String date = df.format(new Date(time));
        return cmdBool("[ -e \"$__F_\" ] && touch -t " + date + " \"$__F_\"");
    }

    /**
     * Returns an array of strings naming the files and directories in the
     * directory denoted by this abstract pathname.
     * <p>
     * Requires command {@code ls}.
     * @see File#list()
     */
    @Override
    public String[] list() {
        return list(null);
    }

    /**
     * Returns an array of strings naming the files and directories in the
     * directory denoted by this abstract pathname that satisfy the specified filter.
     * <p>
     * Requires command {@code ls}.
     * @see File#list(FilenameFilter)
     */
    @Override
    public String[] list(FilenameFilter filter) {
        if (!isDirectory())
            return null;
        FilenameFilter defFilter = (file, name) -> name.equals(".") || name.equals("..");
        List<String> out = Shell.su(setCmd("ls -a \"$__F_\"")).to(new LinkedList<>(), null)
                .exec().getOut();
        String name;
        for (ListIterator<String> it = out.listIterator(); it.hasNext();) {
            name = it.next();
            if (filter != null && !filter.accept(this, name)) {
                it.remove();
                continue;
            }
            if (defFilter.accept(this, name))
                it.remove();
        }
        return out.toArray(new String[0]);
    }

    /**
     * Returns an array of abstract pathnames denoting the files in the
     * directory denoted by this abstract pathname.
     * <p>
     * Requires command {@code ls}.
     * @see File#listFiles()
     */
    @Override
    public SuFile[] listFiles() {
        if (!isDirectory())
            return null;
        String[] ss = list();
        if (ss == null) return null;
        int n = ss.length;
        SuFile[] fs = new SuFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new SuFile(this, ss[i]);
        }
        return fs;
    }

    /**
     * Returns an array of abstract pathnames denoting the files in the
     * directory denoted by this abstract pathname that satisfy the specified filter.
     * <p>
     * Requires command {@code ls}.
     * @see File#listFiles(FilenameFilter)
     */
    @Override
    public SuFile[] listFiles(FilenameFilter filter) {
        if (!isDirectory())
            return null;
        String[] ss = list(filter);
        if (ss == null) return null;
        int n = ss.length;
        SuFile[] fs = new SuFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new SuFile(this, ss[i]);
        }
        return fs;
    }

    /**
     * Returns an array of abstract pathnames denoting the files in the
     * directory denoted by this abstract pathname that satisfy the specified filter.
     * <p>
     * Requires command {@code ls}.
     * @see File#listFiles(FileFilter)
     */
    @Override
    public SuFile[] listFiles(FileFilter filter) {
        String[] ss = list();
        if (ss == null) return null;
        ArrayList<SuFile> files = new ArrayList<>();
        for (String s : ss) {
            SuFile f = new SuFile(this, s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(new SuFile[0]);
    }
}
