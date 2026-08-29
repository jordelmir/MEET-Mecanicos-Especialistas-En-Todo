package com.elysium369.meet.core.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SyncDeadLetterEntity — Isolates permanent sync failures in Room
 * without silently dropping data or returning false success.
 */
@Entity(tableName = "sync_dead_letters")
data class SyncDeadLetterEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val aggregateId: String,
    val operation: String,
    val payloadHash: String,
    val errorCode: String,
    val attemptCount: Int,
    val firstFailedAt: Long,
    val lastFailedAt: Long,
    val resolutionState: String = "UNRESOLVED", // UNRESOLVED, DISCARDED, RETRIED
)
