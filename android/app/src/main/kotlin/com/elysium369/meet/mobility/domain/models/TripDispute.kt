package com.elysium369.meet.mobility.domain.models

import java.time.Instant
import java.util.UUID

enum class TripDisputeState {
    OPEN,
    INVESTIGATING,
    RESOLVED_RIDER,
    RESOLVED_PROVIDER,
    CLOSED,
}

data class TripDispute(
    val disputeId: UUID,
    val tripId: UUID,
    val openedBy: UUID,
    val state: TripDisputeState,
    val createdAt: Instant,
    val updatedAt: Instant,
)
