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

sealed interface GatewayFailure {
    data class Retryable(
        val cause: Throwable,
        val retryAfterMillis: Long? = null,
    ) : GatewayFailure

    data class Authentication(
        val message: String,
    ) : GatewayFailure

    data class Protocol(
        val message: String,
        val cause: Throwable? = null,
    ) : GatewayFailure

    data class Terminal(
        val message: String,
    ) : GatewayFailure
}

object MobilityFailureClassifier {
    fun classify(t: Throwable): GatewayFailure {
        return when (t) {
            is kotlinx.coroutines.CancellationException -> throw t

            is java.net.SocketTimeoutException ->
                GatewayFailure.Retryable(t)

            is java.net.ConnectException ->
                GatewayFailure.Retryable(t)

            is java.net.UnknownHostException ->
                GatewayFailure.Retryable(t)

            is kotlinx.serialization.SerializationException ->
                GatewayFailure.Protocol(
                    message = "Server response violates mobility protocol",
                    cause = t,
                )

            else ->
                GatewayFailure.Terminal(
                    message = "Unclassified mobility failure: ${t::class.java.name}",
                )
        }
    }
}
