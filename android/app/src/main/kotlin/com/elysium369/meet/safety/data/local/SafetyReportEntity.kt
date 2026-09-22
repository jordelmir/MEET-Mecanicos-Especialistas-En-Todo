package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_reports",
    indices = [
        Index(value = ["ownerUserId", "createdAt"]),
        Index(value = ["syncState"]),
    ],
)
data class SafetyReportEntity(
    @PrimaryKey
    val reportId: String,
    val ownerUserId: String,
    val category: String,
    val payloadId: String,
    val occurredAt: Long?,
    val localState: String,
    val serverState: String?,
    val serverVersion: Long,
    val syncState: String,
    val createdAt: Long,
    val updatedAt: Long,
)
