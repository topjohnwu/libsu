package com.topjohnwu.superuser.internal;

public class Constants {
    static final String CMDLINE_START_SERVICE = "start";
    static final String CMDLINE_START_DAEMON = "daemon";
    static final String CMDLINE_STOP_SERVICE = "stop";

    // Put "libsu" in front of the service name to prevent possible conflicts
    static String getServiceName(String pkg) {
        return "libsu-" + pkg;
    }
}
