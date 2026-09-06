package com.elysium369.meet.mobility.domain.guest

import java.time.Instant
import java.util.UUID

@JvmInline
value class E164PhoneNumber(val value: String) {
    init {
        require(REGEX.matches(value)) {
            "Invalid E.164 phone number: $value. Must match +[1-9][0-9]{7,14}"
        }
    }

    companion object {
        private val REGEX = Regex("^\\+[1-9][0-9]{7,14}$")

        fun of(value: String): E164PhoneNumber = E164PhoneNumber(value)
    }
}

data class GuestRideProfile(
    val guestRideId: UUID,
    val rideRequestId: UUID,
    val requestedByRiderId: UUID,
    val guestName: String,
    val guestPhone: E164PhoneNumber,
    val smsNotificationsEnabled: Boolean = true,
    val trackingToken: String,
    val createdAt: Instant,
) {
    init {
        require(guestName.isNotBlank()) { "Guest name cannot be blank" }
        require(trackingToken.isNotBlank()) { "Tracking token cannot be blank" }
    }
}

data class MaskedCommunicationSession(
    val sessionId: UUID,
    val tripId: UUID,
    val riderId: UUID,
    val driverId: UUID,
    val virtualProxyNumber: String,
    val isActive: Boolean = true,
    val expiresAt: Instant,
    val createdAt: Instant,
) {
    init {
        require(virtualProxyNumber.isNotBlank()) { "Virtual proxy number cannot be blank" }
        require(expiresAt > createdAt) { "Session expiry must be after creation" }
    }
}
