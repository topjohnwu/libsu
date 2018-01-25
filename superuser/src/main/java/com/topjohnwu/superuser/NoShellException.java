package com.topjohnwu.superuser;

/**
 * Thrown when it is impossible to construct {@code Shell}.
 * This is a runtime exception, and should happen very rarely.
 */

public class NoShellException extends RuntimeException {

    public NoShellException() {
        super("Impossible to create a shell!");
    }
}
