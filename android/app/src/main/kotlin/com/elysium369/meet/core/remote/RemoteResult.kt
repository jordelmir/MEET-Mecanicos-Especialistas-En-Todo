package com.elysium369.meet.core.remote

import kotlinx.serialization.Serializable

/**
 * RemoteResult — Canonical sealed hierarchy for all Elysium Server & Remote Gateway calls.
 * Eliminates error-collapsing (catch -> null/false/emptyList).
 */
@Serializable
sealed interface RemoteResult<out T> {

    @Serializable
    data class Success<T>(val value: T) : RemoteResult<T>

    @Serializable
    data object NotFound : RemoteResult<Nothing>

    @Serializable
    data object Offline : RemoteResult<Nothing>

    @Serializable
    data object Unauthorized : RemoteResult<Nothing>

    @Serializable
    data class Forbidden(val code: String, val message: String? = null) : RemoteResult<Nothing>

    @Serializable
    data class Rejected(val code: String, val message: String? = null, val retryable: Boolean = false) : RemoteResult<Nothing>

    @Serializable
    data class VersionConflict(val expectedVersion: Long, val actualVersion: Long?) : RemoteResult<Nothing>

    @Serializable
    data class TransportFailure(val code: String, val message: String? = null) : RemoteResult<Nothing>

    @Serializable
    data class ServerFailure(val code: String, val message: String? = null) : RemoteResult<Nothing>
}
