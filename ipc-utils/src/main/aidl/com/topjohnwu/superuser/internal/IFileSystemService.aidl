// IFileSystemService.aidl
package com.topjohnwu.superuser.internal;

interface IFileSystemService {
    Bundle getCanonicalPath(String path);
    boolean isDirectory(String path);
    boolean isFile(String path);
    boolean isHidden(String path);
    long lastModified(String path);
    long length(String path);
    Bundle createNewFile(String path);
    boolean delete(String path);
    String[] list(String path);
    boolean mkdir(String path);
    boolean mkdirs(String path);
    boolean renameTo(String path, String dest);
    boolean setLastModified(String path, long time);
    boolean setPermission(String path, int access, boolean enable, boolean ownerOnly);
    boolean setReadOnly(String path);
    boolean checkAccess(String path, int access);
    long getTotalSpace(String path);
    long getFreeSpace(String path);
    long getUsableSpace(String path);
}
