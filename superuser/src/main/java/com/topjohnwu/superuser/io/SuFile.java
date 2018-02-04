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
import com.topjohnwu.superuser.internal.LibUtils;

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
 * via root shells without messing with command-lines. When a new {@code SuFile} is constructed, it
 * will first check whether the abstract pathname is accessible without root privileges. The
 * checks are: whether the process have rw permission to the path, and whether the parent directory
 * is writable so we can create/delete the target. If any of the checks doesn't pass, all operations
 * will then be backed with root shell commands instead of the native implementations.
 * If you want to bypass the check and always use the root shell for all methods, use
 * {@link #SuFile(File, boolean)} to construct the instance.
 * <p>
 * This class has exact same behavior as a normal {@link File}, however all atomic promises in the
 * parent class does not apply if the actual operations are done via a shell. This is a limitation
 * of using shells, be aware of it.
 * <p>
 * If a root shell is required, it will get a {@code Shell} instance via {@link Shell#getShell()}.
 * The shell backed operations rely on the following tools: {@code rm}, {@code rmdir},
 * {@code mv}, {@code ls}, {@code mkdir}, and {@code touch}. These are all available on
 * modern Android versions (tested on Lollipop+), earlier versions might need to install additional
 * {@code busybox} to make things work properly.
 */
public class SuFile extends File {

    private boolean useShell = true;

    public SuFile(@NonNull String pathname) {
        super(pathname);
        checkShell();
    }

    public SuFile(String parent, @NonNull String child) {
        super(parent, child);
        checkShell();
    }

    public SuFile(File parent, @NonNull String child) {
        super(parent, child);
        checkShell();
    }

    public SuFile(@NonNull URI uri) {
        super(uri);
        checkShell();
    }

    /**
     * Create a new {@code SuFile} using the path of the given {@code File}.
     * @param file the base file.
     */
    public SuFile(@NonNull File file) {
        super(file.getAbsolutePath());
        checkShell();
    }

    /**
     * Create a new {@code SuFile} using the path of the given {@code File}.
     * @param file the base file.
     * @param shell whether use shell for operations.
     */
    public SuFile(@NonNull File file, boolean shell) {
        super(file.getAbsolutePath());
        useShell = shell;
    }

    private void checkShell() {
        // We at least need to be able to rw and also be able to write to parent
        useShell = (!super.canRead() || !super.canWrite() || !super.getParentFile().canWrite())
                && Shell.rootAccess();
    }

    private List<String> runCmd(String cmd) {
        return LibUtils.runCmd(Shell.getShell(),
                cmd.replace("%file%", "'" + getAbsolutePath() + "'"));
    }

    private boolean cmdBoolean(String cmd) {
        List<String> out = runCmd(cmd + " && echo true || echo false");
        return LibUtils.isValidOutput(out) && Boolean.parseBoolean(out.get(out.size() - 1));
    }

    private class Attributes {
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
        List<String> out = runCmd("ls -ld %file%");
        Attributes a = new Attributes();
        if (!LibUtils.isValidOutput(out))
            return a;
        String[] toks = out.get(out.size() - 1).split("\\s+");
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
        return useShell ? cmdBoolean("[ -x %file% ]") : super.canExecute();
    }

    @Override
    public boolean canRead() {
        return useShell ? exists() : super.canRead();
    }

    @Override
    public boolean canWrite() {
        return useShell ? exists() : super.canWrite();
    }

    @Override
    public boolean createNewFile() throws IOException {
        return useShell ? cmdBoolean("[ ! -e %file% ] && touch %file%") : super.createNewFile();
    }

    @Override
    public boolean delete() {
        return useShell ? cmdBoolean("rm -f %file% || rmdir -f %file%") : super.delete();
    }

    public boolean deleteRecursive() {
        return cmdBoolean("rm -rf %file%");
    }

    @Override
    public void deleteOnExit() {}

    @Override
    public boolean exists() {
        return useShell ? cmdBoolean("[ -e %file% ]") : super.exists();
    }

    @NonNull
    @Override
    public SuFile getAbsoluteFile() {
        return new SuFile(getAbsolutePath());
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
        return useShell ? Long.MAX_VALUE : super.getFreeSpace();
    }

    @Override
    public long getTotalSpace() {
        return useShell ? Long.MAX_VALUE : super.getTotalSpace();
    }

    @Override
    public long getUsableSpace() {
        return useShell ? Long.MAX_VALUE : super.getUsableSpace();
    }

    @Override
    public boolean isDirectory() {
        return useShell ? cmdBoolean("[ -d %file% ]") : super.isDirectory();
    }

    @Override
    public boolean isFile() {
        return useShell ? cmdBoolean("[ -f %file% ]") : super.isFile();
    }

    @Override
    public long lastModified() {
        return useShell ? getAttributes().time : super.lastModified();
    }

    @Override
    public long length() {
        return useShell ? getAttributes().size : super.length();
    }

    @Override
    public boolean mkdir() {
        return useShell ? cmdBoolean("mkdir %file%") : super.mkdir();
    }

    @Override
    public boolean mkdirs() {
        return useShell ? cmdBoolean("mkdir -p %file%") : super.mkdirs();
    }

    @Override
    public boolean renameTo(File dest) {
        return useShell ? cmdBoolean("mv -f %file% '" + dest.getAbsolutePath() + "'")
                : super.renameTo(dest);
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
        return cmdBoolean("chmod " + new String(a.perms) + " %file%");
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        return useShell ? setPerms(executable, ownerOnly, 0x1)
                : super.setExecutable(executable, ownerOnly);
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        return useShell ? setPerms(readable, ownerOnly, 0x4)
                : super.setReadable(readable, ownerOnly);
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        return useShell ? setPerms(writable, ownerOnly, 0x2)
                : super.setWritable(writable, ownerOnly);
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
        if (useShell) {
            DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String date = df.format(new Date(time));
            return cmdBoolean("[ -e %file% ] && touch -t " + date + " %file%");
        } else {
            return super.setLastModified(time);
        }
    }

    @Override
    public String[] list() {
        if (useShell && isDirectory()) {
            List<String> out = runCmd("ls %file%");
            if (!LibUtils.isValidOutput(out))
                return null;
            return out.toArray(new String[0]);
        } else {
            return super.list();
        }
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
