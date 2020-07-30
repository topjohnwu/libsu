// IRootIPC.aidl
package com.topjohnwu.superuser.internal;

// Declare any non-default types here with import statements

interface IRootIPC {
    oneway void broadcast();
    IBinder bind(in Intent intent);
    oneway void unbind();
    oneway void stop();
}
