// IFileSystemService.aidl
package com.topjohnwu.superuser.internal;

parcelable ParcelValues;

interface IFileSystemService {
    // File APIs
    /* (err, String) */ ParcelValues getCanonicalPath(String path);
    boolean isDirectory(String path);
    boolean isFile(String path);
    boolean isHidden(String path);
    long lastModified(String path);
    long length(String path);
    /* (err, bool) */ ParcelValues createNewFile(String path);
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
    int getMode(String path);
    /* (err, bool) */ ParcelValues createLink(String link, String target, boolean soft);

    // I/O APIs
    /* (err, int) */ ParcelValues open(String path, int mode, String fifo);
    oneway void close(int handle);
    /* (err, int) */ ParcelValues pread(int handle, int len, long offset);
    /* (err, int) */ ParcelValues pwrite(int handle, int len, long offset);
    /* (err, long) */ ParcelValues lseek(int handle, long offset, int whence);
    /* (err, long) */ ParcelValues size(int handle);
    /* (err) */ ParcelValues ftruncate(int handle, long length);
    /* (err) */ ParcelValues sync(int handle, boolean metaData);
}
