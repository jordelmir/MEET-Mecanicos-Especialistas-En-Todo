package com.elysium369.meet.core.sync

import com.elysium369.meet.core.remote.RemoteResult

/**
 * SyncResult — Canonical sealed hierarchy for sync/outbox operations.
 *
 * Every WorkManager worker and outbox dispatcher must return SyncResult.
 * Retryable failures go back to the queue. Permanent failures become dead letters.
 * Authentication failures trigger controlled auth flow — never silent retry.
 */
sealed interface SyncResult<out T> {

    data class Success<T>(val value: T) : SyncResult<T>

    data class RetryableFailure(
        val code: String,
        val message: String? = null,
        val attemptCount: Int = 1,
        val nextRetryAtEpochMs: Long? = null,
    ) : SyncResult<Nothing>

    data class PermanentFailure(
        val code: String,
        val message: String? = null,
        val deadLetterId: String? = null,
    ) : SyncResult<Nothing>

    data class Conflict(
        val code: String,
        val serverVersion: Long?,
        val localVersion: Long?,
        val message: String? = null,
    ) : SyncResult<Nothing>

    data object AuthenticationRequired : SyncResult<Nothing>
}

/**
 * Bridge from RemoteResult to SyncResult for outbox/workers.
 */
fun <T> RemoteResult<T>.toSyncResult(
    domain: String,
    attemptCount: Int = 1,
): SyncResult<T> = when (this) {
    is RemoteResult.Success -> SyncResult.Success(value)
    is RemoteResult.NotFound -> SyncResult.PermanentFailure(
        code = "NOT_FOUND",
        message = "Resource not found on server",
    )
    is RemoteResult.Offline -> SyncResult.RetryableFailure(
        code = "OFFLINE",
        message = "No network connection",
        attemptCount = attemptCount,
    )
    is RemoteResult.Unauthorized -> SyncResult.AuthenticationRequired
    is RemoteResult.Forbidden -> SyncResult.PermanentFailure(
        code = code,
        message = message,
    )
    is RemoteResult.Rejected -> if (retryable) {
        SyncResult.RetryableFailure(
            code = code,
            message = message,
            attemptCount = attemptCount,
        )
    } else {
        SyncResult.PermanentFailure(code = code, message = message)
    }
    is RemoteResult.VersionConflict -> SyncResult.Conflict(
        code = "VERSION_CONFLICT",
        serverVersion = actualVersion,
        localVersion = expectedVersion,
        message = "Server version $actualVersion differs from expected $expectedVersion",
    )
    is RemoteResult.TransportFailure -> SyncResult.RetryableFailure(
        code = code,
        message = message,
        attemptCount = attemptCount,
    )
    is RemoteResult.ServerFailure -> SyncResult.RetryableFailure(
        code = code,
        message = message,
        attemptCount = attemptCount,
    )
}
