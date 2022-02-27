package com.topjohnwu.superuser

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Get the main shell instance in a coroutine.
 * @see Shell.getShell
 */
suspend fun retrieveShell(): Shell {
    return suspendCoroutine { continuation ->
        Shell.getShell { shell ->
            continuation.resume(shell)
        }
    }
}

/**
 * Execute the [job][Shell.Job] in a coroutine and return its result.
 * @see Shell.Job.submit
 */
suspend fun Shell.Job.await(): Shell.Result {
    return suspendCoroutine { continuation ->
        submit { result ->
            continuation.resume(result)
        }
    }
}