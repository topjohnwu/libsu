package com.topjohnwu.superuser;

/**
 * Created by topjohnwu on 2018/1/19.
 */

public class NoShellException extends Exception {

    public NoShellException() {
        super("Unable to start a shell!");
    }
}
