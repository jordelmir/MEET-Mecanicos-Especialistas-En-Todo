package com.elysium369.meet.ride.data.local

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "ride_command_outbox",
    primaryKeys = ["idempotencyKey"],
    indices = [
        Index(value = ["rideId", "createdAt"]),
        Index(value = ["status", "nextAttemptAt"]),
        Index(value = ["actorSessionUserId", "status"]),
    ],
)
@Serializable
data class RideCommandOutboxEntity(
    val idempotencyKey: String,
    val rideId: String,
    val actorSessionUserId: String,
    val commandType: String,
    val expectedVersion: Long,
    val payloadVersion: Int,
    val payloadJson: String,
    val status: String,
    val attemptCount: Int,
    val nextAttemptAt: Long,
    val leaseStartedAt: Long?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val correlationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

object RideOutboxStatus {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"
    const val RETRYABLE = "RETRYABLE"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
    const val DEAD_LETTER = "DEAD_LETTER"

    val ready = listOf(PENDING, RETRYABLE)
}
