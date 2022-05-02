// ITestService.aidl
package com.topjohnwu.libsuexample;

// Declare any non-default types here with import statements

interface ITestService {
    int getPid();
    int getUid();
    String getUUID();
    IBinder getFileSystemService();
}
