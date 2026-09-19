package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_command_outbox",
    indices = [
        Index(value = ["status", "nextAttemptAt"]),
        Index(value = ["aggregateId", "createdAt"]),
        Index(value = ["actorSessionUserId", "status"]),
    ],
)
data class SafetyCommandOutboxEntity(
    @PrimaryKey
    val idempotencyKey: String,
    val aggregateId: String,
    val actorSessionUserId: String,
    val commandType: String,
    val expectedVersion: Long,
    val payloadVersion: Int,
    val payloadId: String,
    val clientPayloadSha256: String,
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
