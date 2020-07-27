// IRootIPC.aidl
package com.topjohnwu.superuser.internal;

// Declare any non-default types here with import statements

interface IRootIPC {
    void broadcast();
    IBinder bind(in Intent intent, IBinder client);
    void unbind();
    void stop();
}
