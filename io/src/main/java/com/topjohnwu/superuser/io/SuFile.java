/*
 * Copyright 2019 John "topjohnwu" Wu
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
import java.io.IOException;
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
 * All methods for this class will be backed by executing commands via the global root shell.
 * The {@code Shell} instance will be acquired via {@link Shell#getShell()}.
 * <p>
 * This class has the exact same behavior as a normal {@link File}, however non of the operations
 * are atomic. This is a limitation of using shells, be aware of it.
 * <p>
 * For full functionality, the environment require: {@code rm}, {@code rmdir}, {@code readlink},
 * {@code mv}, {@code ls}, {@code mkdir}, {@code touch}, {@code stat}. For some methods,
 * {@code blockdev} could be utilized for additional functionality.
 * These tools are available on modern Android versions with toolbox, however for optimal results
 * installing {@code busybox}, especially the one bundled with libsu busybox module
 * would be desirable. Some operations could have oddities without busybox due to the lack of tools
 * or inconsistency of the tools, check the method descriptions for more info before using it.
 * <p>
 * There are also handy factory methods {@code SuFile.open(...)} for obtaining {@link File}
 * instances. These factory methods will return a normal {@link File} instance if the underlying
 * shell does not have root access, or else returns a {@link SuFile} instance.
 */
public class SuFile extends File {

    private static int shellHash = -1;
    private static boolean stat, blockdev, wc;

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
        Shell shell = Shell.getShell();
        if (shell.hashCode() != shellHash) {
            shellHash = shell.hashCode();
            // Check tools
            stat = ShellUtils.fastCmdResult(shell, "command -v stat");
            blockdev = ShellUtils.fastCmdResult(shell, "command -v blockdev");
            wc = ShellUtils.fastCmdResult(shell, "command -v wc");
        }
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

    private String[] genCmd(String... cmds) {
        boolean needCfile = false;
        for (String cmd : cmds) {
            if (cmd.contains("$CFILE")) {
                needCfile = true;
                break;
            }
        }
        String[] newCmd = new String[cmds.length + 1];
        if (needCfile) {
            newCmd[0] = String.format("FILE='%s';CFILE=\"`readlink -f \"$FILE\"`\"", getAbsolutePath());
        } else {
            newCmd[0] = String.format("FILE='%s'", getAbsolutePath());
        }
        System.arraycopy(cmds, 0, newCmd, 1, cmds.length);
        return newCmd;
    }

    private String cmd(String... cmds) {
        return ShellUtils.fastCmd(genCmd(cmds));
    }

    private boolean cmdBoolean(String c) {
        return ShellUtils.fastCmdResult(genCmd(c));
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
        boolean origImpl = false;
        try {
            origImpl = super.createNewFile();
        } catch (IOException ignored) { }
        return origImpl || cmdBoolean("[ ! -e \"$FILE\" ] && touch \"$FILE\"");
    }

    @Override
    public boolean delete() {
        return cmdBoolean("rm -f \"$FILE\" || rmdir -f \"$FILE\"");
    }

    public boolean clear() {
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
    public SuFile getAbsoluteFile() {
        return this;
    }

    @NonNull
    @Override
    public String getCanonicalPath() {
        String path = cmd("echo \"$CFILE\"");
        return path.isEmpty() ? getAbsolutePath() : path;
    }

    @NonNull
    @Override
    public SuFile getCanonicalFile() {
        return new SuFile(getCanonicalPath());
    }

    @Override
    public SuFile getParentFile() {
        return new SuFile(getParent());
    }

    private long statFS(String fmt) {
        if (!stat)
            return Long.MAX_VALUE;
        String[] res = cmd("stat -fc '%S " + fmt + "' \"$FILE\"").split(" ");
        if (res.length != 2)
            return Long.MAX_VALUE;
        return Long.parseLong(res[0]) * Long.parseLong(res[1]);
    }

    @Override
    public long getFreeSpace() {
        return statFS("%f");
    }

    @Override
    public long getTotalSpace() {
        return statFS("%b");
    }

    @Override
    public long getUsableSpace() {
        return statFS("%a");
    }

    @Override
    public boolean isDirectory() {
        return cmdBoolean("[ -d \"$FILE\" ]");
    }

    @Override
    public boolean isFile() {
        return cmdBoolean("[ -f \"$FILE\" ]");
    }

    public boolean isBlock() {
        return cmdBoolean("[ -b \"$FILE\" ]");
    }

    public boolean isCharacter() {
        return cmdBoolean("[ -c \"$FILE\" ]");
    }

    @Override
    public long lastModified() {
        try {
            if (!stat)
                return 0L;
            return Long.parseLong(cmd("stat -Lc '%Y' \"$FILE\"")) * 1000;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public long length() {
        try {
            if (stat) {
                // Support block size
                if (blockdev) {
                    return Long.parseLong(cmd("[ -b \"$FILE\" ] && " +
                            "blockdev --getsize64 \"$FILE\" || " +
                            "stat -Lc '%s' \"$FILE\""));
                } else {
                    return Long.parseLong(cmd("stat -Lc '%s' \"$FILE\""));
                }
            } else if (wc) {
                return Long.parseLong(cmd("[ -f \"$FILE\" ] && wc -c < \"$FILE\" || echo 0"));
            } else {
                return 0L;
            }
        } catch (NumberFormatException e) {
            return 0L;
        }
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
        if (!stat)
            return false;
        char[] perms = cmd("stat -Lc '%a' \"$FILE\"").toCharArray();
        for (int i = 0; i < perms.length; ++i) {
            int perm = perms[i] - '0';
            if (set && (!ownerOnly || i == 0))
                perm |= b;
            else
                perm &= ~(b);
            perms[i] = (char) (perm + '0');
        }
        return cmdBoolean("chmod " + new String(perms) + " \"$CFILE\"");
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
     * coreutils, which is the same in toolbox and busybox, so the operation
     * might fail on older Android versions without busybox.
     * @param time The new last-modified time, measured in milliseconds since the epoch.
     * @return {@code true} if and only if the operation succeeded; {@code false} otherwise.
     */
    @Override
    public boolean setLastModified(long time) {
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
        String date = df.format(new Date(time));
        return cmdBoolean("[ -e \"$FILE\" ] && touch -t " + date + " \"$CFILE\"");
    }

    @Override
    public String[] list() {
        return list(null);
    }

    @Override
    public String[] list(FilenameFilter filter) {
        if (!isDirectory())
            return null;
        FilenameFilter defFilter = (file, name) -> name.equals(".") || name.equals("..");
        List<String> out = Shell.su(genCmd("ls -a \"$FILE\"")).to(new LinkedList<>(), null)
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
