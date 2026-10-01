package com.elysium369.meet.communications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Hardware must stop even when the network is unavailable or the caller was cancelled. */
internal suspend fun cleanupCallResources(stopLocal: () -> Unit, remoteCleanup: suspend () -> Unit) {
    stopLocal()
    withContext(NonCancellable) {
        withTimeoutOrNull(5_000L) {
            try { remoteCleanup() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Local teardown is already complete. */ }
        }
    }
}
