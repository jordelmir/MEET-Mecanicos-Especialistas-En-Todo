package com.elysium369.meet.mobility.domain.chat

import java.time.Instant
import java.util.UUID

enum class TripMessageType {
    TEXT,
    LOCATION_SHARE,
    SYSTEM_ALERT,
}

data class TripMessage(
    val messageId: UUID,
    val tripId: UUID,
    val senderId: UUID,
    val messageType: TripMessageType,
    val body: String,
    val createdAt: Instant,
) {
    init {
        require(body.isNotBlank()) { "Message body cannot be blank" }
        require(body.length <= 1000) { "Message body cannot exceed 1000 characters" }
    }
}
