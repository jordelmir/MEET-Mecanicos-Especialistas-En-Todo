package com.elysium369.meet.mobility.domain.result

enum class MobilityErrorCode {
    UNAUTHENTICATED,
    FORBIDDEN,
    MARKET_NOT_AVAILABLE,
    SERVICE_NOT_AVAILABLE,
    MARKETPLACE_NOT_AVAILABLE,
    SCHEDULED_RIDE_NOT_AVAILABLE,
    DRIVER_NOT_ELIGIBLE,
    VEHICLE_NOT_ELIGIBLE,
    PAIR_BLOCKED,
    PRESENCE_STALE,
    CONCURRENCY_CONFLICT,
    ALREADY_MATCHED,
    OFFER_EXPIRED,
    INVALID_TRANSITION,
    PIN_REQUIRED,
    PIN_INVALID,
    TRIP_NOT_FOUND,
    RIDE_REQUEST_NOT_FOUND,
    NETWORK_ERROR,
    UNKNOWN_ERROR,
}

sealed interface MobilityCommandResult<out T> {
    data class Accepted<T>(
        val value: T,
        val serverVersion: Long,
        val canonicalReceiptId: String? = null,
    ) : MobilityCommandResult<T>

    data class Conflict(
        val currentVersion: Long,
        val message: String = "Concurrency conflict: resource was modified",
    ) : MobilityCommandResult<Nothing>

    data class Rejected(
        val code: MobilityErrorCode,
        val message: String? = null,
    ) : MobilityCommandResult<Nothing>

    data class RetryableFailure(
        val cause: Throwable,
        val message: String? = cause.message,
    ) : MobilityCommandResult<Nothing>
}
