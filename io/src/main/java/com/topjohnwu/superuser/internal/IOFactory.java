package com.topjohnwu.superuser.internal;

import java.io.File;
import java.io.FileNotFoundException;

public final class IOFactory {
    private IOFactory() {}

    public static ShellFileIO createShellFileIO(ShellFile file, String mode) throws FileNotFoundException {
        String internalMode;
        switch (mode) {
            case "r":
                internalMode = "r";
                break;
            case "rw":
            case "rws":
            case "rwd":
                internalMode = "rw";
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + mode);
        }
        return new ShellFileIO(file, internalMode);
    }

    public static RandomAccessFileWrapper createRandomAccessFileWrapper(File file, String mode)
            throws FileNotFoundException {
        return new RandomAccessFileWrapper(file, mode);
    }

    public static ShellInputStream createShellInputStream(ShellFile file) throws FileNotFoundException {
        return new ShellInputStream(file);
    }

    public static ShellOutputStream createShellOutputStream(ShellFile file, boolean append)
            throws FileNotFoundException {
        return new ShellOutputStream(file, append);
    }

    public static ShellFile createShellFile(File file) {
        return new ShellFile(file);
    }
}
