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

import android.support.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A {@link File} implementation with root access.
 * <p>
 * This class is meant to be used just like a normal {@link File}, so developers can access files
 * via root shells without messing with command-lines.
 * <p>
 * This class has the exact same behavior as a normal {@link File}, however non of the operations
 * are atomic. This is a limitation of using shells, be aware of it.
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

    private static int shellHash = -1;
    private static boolean stat, blockdev;

    /**
     * Create a new {@code SuFile} using the path of the given {@code File}.
     * @param file the base file.
     */
    public SuFile(@NonNull File file) {
        super(file.getAbsolutePath());
        Shell shell = Shell.getShell();
        if (shell.hashCode() != shellHash) {
            shellHash = shell.hashCode();
            // Check tools
            stat = ShellUtils.fastCmdResult(shell, "command -v stat");
            blockdev = ShellUtils.fastCmdResult(shell, "command -v blockdev");
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

    private String genCmd(String cmd) {
        return cmd
                .replace("//file//", "'" + getAbsolutePath() + "'")
                .replace("//canfile//", "\"`readlink -f '" + getAbsolutePath() + "'`\"");
    }

    private String cmd(String cmd) {
        return ShellUtils.fastCmd(genCmd(cmd));
    }

    private boolean cmdBoolean(String cmd) {
        return ShellUtils.fastCmdResult(genCmd(cmd));
    }

    private static class Attributes {
        char[] perms = new char[3];
        String owner = "";
        String group = "";
        long size = 0L;
        long time = 0L;

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "%s %s.%s %d %d", new String(perms), owner, group, size, time);
        }
    }


    private Attributes getAttributes() {
        String lsInfo = cmd("ls -ld //canfile//");
        Attributes a = new Attributes();
        if (lsInfo == null)
            return a;
        String[] toks = lsInfo.split("\\s+");
        int idx = 0;
        for (int i = 0; i < 9; i += 3) {
            int perm = 0;
            if (toks[idx].charAt(i + 1) != '-')
                perm |= 0x4;
            if (toks[idx].charAt(i + 2) != '-')
                perm |= 0x2;
            if (toks[idx].charAt(i + 3) != '-')
                perm |= 0x1;
            a.perms[i / 3] = (char) (perm + '0');
        }
        ++idx;
        // There might be links info, we don't want it
        if (toks.length > 7)
            ++idx;
        a.owner = toks[idx++];
        a.group = toks[idx++];
        a.size = Long.parseLong(toks[idx++]);
        try {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            a.time = df.parse(toks[idx++] + " " + toks[idx++]).getTime();
        } catch (ParseException ignored) {}

        return a;
    }

    @Override
    public boolean canExecute() {
        return cmdBoolean("[ -x //file// ]");
    }

    @Override
    public boolean canRead() {
        return cmdBoolean("[ -r //file// ]");
    }

    @Override
    public boolean canWrite() {
        return cmdBoolean("[ -w //file// ]");
    }

    @Override
    public boolean createNewFile() {
        return cmdBoolean("[ ! -e //file// ] && touch //file//");
    }

    @Override
    public boolean delete() {
        return cmdBoolean("rm -f //file// || rmdir -f //file//");
    }

    public boolean deleteRecursive() {
        return cmdBoolean("rm -rf //file//");
    }

    @Override
    public void deleteOnExit() {}

    @Override
    public boolean exists() {
        return cmdBoolean("[ -e //file// ]");
    }

    @NonNull
    @Override
    public SuFile getAbsoluteFile() {
        return this;
    }

    @NonNull
    @Override
    public String getCanonicalPath() throws IOException {
        String path = cmd("echo //canfile//");
        return path == null ? getAbsolutePath() : path;
    }

    @NonNull
    @Override
    public SuFile getCanonicalFile() throws IOException {
        return new SuFile(getCanonicalPath());
    }

    @Override
    public SuFile getParentFile() {
        return new SuFile(getParent());
    }

    @Override
    public long getFreeSpace() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getTotalSpace() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getUsableSpace() {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean isDirectory() {
        return cmdBoolean("[ -d //file// ]");
    }

    @Override
    public boolean isFile() {
        return cmdBoolean("[ -f //file// ]");
    }

    @Override
    public long lastModified() {
        return getAttributes().time;
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
        return (blockdev && stat ? Long.parseLong(
                cmd("[ -b //file// ] && blockdev --getsize64 //file// || stat -c '%s' //canfile//"))
                : getAttributes().size);
    }

    @Override
    public boolean mkdir() {
        return cmdBoolean("mkdir //file//");
    }

    @Override
    public boolean mkdirs() {
        return cmdBoolean("mkdir -p //file//");
    }

    @Override
    public boolean renameTo(File dest) {
        return cmdBoolean("mv -f //file// '" + dest.getAbsolutePath() + "'");
    }

    private boolean setPerms(boolean set, boolean ownerOnly, int b) {
        Attributes a = getAttributes();
        for (int i = 0; i < a.perms.length; ++i) {
            int perm = a.perms[i] - '0';
            if (set && (!ownerOnly || i == 0))
                perm |= b;
            else
                perm &= ~(b);
            a.perms[i] = (char) (perm + '0');
        }
        return cmdBoolean("chmod " + new String(a.perms) + " //canfile//");
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return setPerms(executable, ownerOnly, 0x1);
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return setPerms(readable, ownerOnly, 0x4);
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return setPerms(writable, ownerOnly, 0x2);
    }

    @Override
    public boolean setReadOnly() {
        return setWritable(false, false) && setExecutable(false, false);
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
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
        String date = df.format(new Date(time));
        return cmdBoolean("[ -e //file// ] && touch -t " + date + " //canfile//");
    }

    @Override
    public String[] list() {
        List<String> out = Shell.Sync.su(genCmd("ls //file//"));
        if (!ShellUtils.isValidOutput(out))
            return null;
        return out.toArray(new String[0]);
    }

    @Override
    public String[] list(FilenameFilter filter) {
        String names[] = list();
        if ((names == null) || (filter == null)) {
            return names;
        }
        List<String> v = new ArrayList<>();
        for (String name : names) {
            if (filter.accept(this, name)) {
                v.add(name);
            }
        }
        return v.toArray(new String[v.size()]);
    }

    @Override
    public SuFile[] listFiles() {
        String[] ss = list();
        if (ss == null) return null;
        int n = ss.length;
        SuFile[] fs = new SuFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new SuFile(this, ss[i]);
        }
        return fs;
    }

    @Override
    public SuFile[] listFiles(FilenameFilter filter) {
        String[] ss = list(filter);
        if (ss == null) return null;
        int n = ss.length;
        SuFile[] fs = new SuFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new SuFile(this, ss[i]);
        }
        return fs;
    }

    @Override
    public SuFile[] listFiles(FileFilter filter) {
        String ss[] = list();
        if (ss == null) return null;
        ArrayList<SuFile> files = new ArrayList<>();
        for (String s : ss) {
            SuFile f = new SuFile(this, s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(new SuFile[files.size()]);
    }
}
