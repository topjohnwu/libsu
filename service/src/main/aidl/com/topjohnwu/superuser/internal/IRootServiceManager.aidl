// IRootServiceManager.aidl
package com.topjohnwu.superuser.internal;

// Declare any non-default types here with import statements

interface IRootServiceManager {
    oneway void broadcast();
    oneway void connect(in Bundle bundle);
    IBinder bind(in Intent intent);
    oneway void unbind(in String className);
    oneway void stop(in String className);
}
