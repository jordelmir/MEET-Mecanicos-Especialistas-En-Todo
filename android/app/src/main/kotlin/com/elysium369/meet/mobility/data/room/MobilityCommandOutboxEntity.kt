package com.elysium369.meet.mobility.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OutboxState {
    PENDING,
    SENDING,
    SERVER_ACCEPTED,
    SERVER_REJECTED,
    CONFLICT,
    FAILED_RETRYABLE,
}

@Entity(
    tableName = "mobility_command_outbox",
    indices = [
        Index(value = ["commandId"], unique = true),
        Index(value = ["state", "nextAttemptAtEpochMs"]),
        Index(value = ["leaseExpiresAtEpochMs"]),
        Index(value = ["correlationId"]),
    ],
)
data class MobilityCommandOutboxEntity(
    @PrimaryKey
    val commandId: String,
    val correlationId: String,
    val commandType: String,
    val aggregateId: String?,
    val expectedServerVersion: Long?,
    val payloadJson: String,
    val state: String,
    val attemptCount: Int,
    val createdAtEpochMs: Long,
    val lastAttemptAtEpochMs: Long?,
    val nextAttemptAtEpochMs: Long?,
    val leaseOwner: String? = null,
    val leaseExpiresAtEpochMs: Long? = null,
    val lastErrorCode: String? = null,
    val schemaVersion: Int = 1,
)
