package com.topjohnwu.superuser.internal;

import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.FileNotFoundException;

public final class IOFactory {
    private IOFactory() {}

    public static ShellFileIO createShellFileIO(SuFile file, String mode) throws FileNotFoundException {
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

    public static ShellInputStream createShellInputStream(SuFile file) throws FileNotFoundException {
        return new ShellInputStream(file);
    }

    public static ShellOutputStream createShellOutputStream(SuFile file, boolean append)
            throws FileNotFoundException {
        return new ShellOutputStream(file, append);
    }
}
