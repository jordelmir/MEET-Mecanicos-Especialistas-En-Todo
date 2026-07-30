package com.elysium369.meet.ride.domain

@JvmInline
value class RideId private constructor(val value: String) {
    companion object {
        fun of(raw: String): RideId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "Ride ID is required" }
            require(normalized.length <= 128) { "Ride ID is too long" }
            return RideId(normalized)
        }
    }
}

@JvmInline
value class RideVersion private constructor(val value: Long) {
    companion object {
        fun of(value: Long): RideVersion {
            require(value >= 0) { "Ride version cannot be negative" }
            return RideVersion(value)
        }
    }
}

@JvmInline
value class RidePayloadVersion private constructor(val value: Int) {
    companion object {
        fun of(value: Int): RidePayloadVersion {
            require(value > 0) { "Payload version must be positive" }
            return RidePayloadVersion(value)
        }
    }
}

@JvmInline
value class RideIdempotencyKey private constructor(val value: String) {
    companion object {
        private val SAFE_SHAPE = Regex("[A-Za-z0-9._:-]{16,128}")

        fun of(raw: String): RideIdempotencyKey {
            val normalized = raw.trim()
            require(SAFE_SHAPE.matches(normalized)) {
                "Idempotency key must contain 16-128 safe characters"
            }
            return RideIdempotencyKey(normalized)
        }
    }
}

enum class RideCommandType {
    CREATE_DRAFT,
    UPDATE_DRAFT,
    PUBLISH,
    SUBMIT_OFFER,
    UPDATE_OFFER,
    WITHDRAW_OFFER,
    ACCEPT_OFFER,
    CLAIM,
    ASSIGN_BY_DISPATCHER,
    REASSIGN,
    DRIVER_EN_ROUTE,
    DRIVER_ARRIVED,
    ISSUE_BOARDING_PIN,
    VERIFY_BOARDING_PIN,
    START,
    COMPLETE,
    CANCEL,
    EXPIRE,
    UPDATE_ROUTE,
    ADD_STOP,
    REMOVE_STOP,
    CHANGE_FARE,
    CONFIRM_PAYMENT,
    OPEN_DISPUTE,
    RATE,
    BLOCK_COUNTERPARTY,
    SAFETY_SIGNAL,
}

data class RideCommandEnvelope(
    val rideId: RideId,
    val expectedVersion: RideVersion,
    val idempotencyKey: RideIdempotencyKey,
    val type: RideCommandType,
    val payloadVersion: RidePayloadVersion,
)
