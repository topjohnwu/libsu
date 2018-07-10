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

package com.topjohnwu.superuser.internal;

import android.support.annotation.NonNull;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShellFile extends File {

    private static int shellHash = -1;
    private static boolean stat, blockdev;

    ShellFile(@NonNull File file) {
        super(file.getAbsolutePath());
        Shell shell = Shell.getShell();
        if (shell.hashCode() != shellHash) {
            shellHash = shell.hashCode();
            // Check tools
            stat = ShellUtils.fastCmdResult(shell, "command -v stat");
            blockdev = ShellUtils.fastCmdResult(shell, "command -v blockdev");
        }
    }

    private ShellFile(@NonNull String pathname) {
        this(new File(pathname));
    }

    private ShellFile(File parent, @NonNull String child) {
        this(new File(parent, child));
    }

    private String[] genCmd(String cmd) {
        String setup;
        if (cmd.contains("$CFILE")) {
            setup = String.format("FILE='%s';CFILE=\"`readlink -f \"$FILE\"`\"", getAbsolutePath());
        } else {
            setup = String.format("FILE='%s'", getAbsolutePath());
        }
        return new String[] { setup, cmd, "FILE=;CFILE=" };
    }

    private String cmd(String c) {
        return ShellUtils.fastCmd(genCmd(c));
    }

    private boolean cmdBoolean(String c) {
        return Boolean.parseBoolean(cmd(c + " >/dev/null 2>&1 && echo true || echo false"));
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
        String lsInfo = cmd("ls -ld \"$CFILE\"");
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
        return cmdBoolean("[ -x \"$FILE\" ]");
    }

    @Override
    public boolean canRead() {
        return cmdBoolean("[ -r \"$FILE\" ]");
    }

    @Override
    public boolean canWrite() {
        return cmdBoolean("[ -w \"$FILE\" ]");
    }

    @Override
    public boolean createNewFile() {
        return cmdBoolean("[ ! -e \"$FILE\" ] && touch \"$FILE\"");
    }

    @Override
    public boolean delete() {
        return cmdBoolean("rm -f \"$FILE\" || rmdir -f \"$FILE\"");
    }

    boolean clear() {
        return cmdBoolean("echo -n > \"$FILE\"");
    }

    public boolean deleteRecursive() {
        return cmdBoolean("rm -rf \"$FILE\"");
    }

    @Override
    public void deleteOnExit() {}

    @Override
    public boolean exists() {
        return cmdBoolean("[ -e \"$FILE\" ]");
    }

    @NonNull
    @Override
    public ShellFile getAbsoluteFile() {
        return this;
    }

    @NonNull
    @Override
    public String getCanonicalPath() {
        String path = cmd("echo \"$CFILE\"");
        return path == null ? getAbsolutePath() : path;
    }

    @NonNull
    @Override
    public ShellFile getCanonicalFile() {
        return new ShellFile(getCanonicalPath());
    }

    @Override
    public ShellFile getParentFile() {
        return new ShellFile(getParent());
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
        return cmdBoolean("[ -d \"$FILE\" ]");
    }

    @Override
    public boolean isFile() {
        return cmdBoolean("[ -f \"$FILE\" ]");
    }

    @Override
    public long lastModified() {
        return getAttributes().time;
    }

    @Override
    public long length() {
        return (blockdev && stat ? Long.parseLong(
                cmd("[ -b \"$FILE\" ] && blockdev --getsize64 \"$FILE\" || stat -c '%s' \"$CFILE\""))
                : getAttributes().size);
    }

    @Override
    public boolean mkdir() {
        return cmdBoolean("mkdir \"$FILE\"");
    }

    @Override
    public boolean mkdirs() {
        return cmdBoolean("mkdir -p \"$FILE\"");
    }

    @Override
    public boolean renameTo(File dest) {
        return cmdBoolean("mv -f \"$FILE\" '" + dest.getAbsolutePath() + "'");
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
        return cmdBoolean("chmod " + new String(a.perms) + " \"$CFILE\"");
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

    @Override
    public boolean setLastModified(long time) {
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
        String date = df.format(new Date(time));
        return cmdBoolean("[ -e \"$FILE\" ] && touch -t " + date + " \"$CFILE\"");
    }

    @Override
    public String[] list() {
        List<String> out = Shell.Sync.su(genCmd("ls \"$FILE\""));
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
    public ShellFile[] listFiles() {
        String[] ss = list();
        if (ss == null) return null;
        int n = ss.length;
        ShellFile[] fs = new ShellFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new ShellFile(this, ss[i]);
        }
        return fs;
    }

    @Override
    public ShellFile[] listFiles(FilenameFilter filter) {
        String[] ss = list(filter);
        if (ss == null) return null;
        int n = ss.length;
        ShellFile[] fs = new ShellFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new ShellFile(this, ss[i]);
        }
        return fs;
    }

    @Override
    public ShellFile[] listFiles(FileFilter filter) {
        String ss[] = list();
        if (ss == null) return null;
        ArrayList<ShellFile> files = new ArrayList<>();
        for (String s : ss) {
            ShellFile f = new ShellFile(this, s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(new ShellFile[files.size()]);
    }
}
