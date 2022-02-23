// IRootServiceManager.aidl
package com.topjohnwu.superuser.internal;

// Declare any non-default types here with import statements

interface IRootServiceManager {
    oneway void broadcast(int uid, String action);
    oneway void stop(in ComponentName name);
    oneway void connect(in Bundle bundle);
    IBinder bind(in Intent intent);
    oneway void unbind(in ComponentName name);
}
