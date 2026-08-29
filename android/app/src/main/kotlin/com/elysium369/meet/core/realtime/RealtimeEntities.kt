package com.elysium369.meet.core.realtime

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "realtime_applied_events")
data class RealtimeAppliedEventEntity(
    @PrimaryKey
    val eventId: String,
    val domain: String,
    val aggregateId: String,
    val aggregateVersion: Long,
    val receivedAt: Long,
)

@Entity(tableName = "realtime_stream_cursors")
data class RealtimeStreamCursorEntity(
    @PrimaryKey
    val streamKey: String, // e.g. "ride/{rideId}"
    val cursor: Long,
    val aggregateVersion: Long,
    val updatedAt: Long,
)
