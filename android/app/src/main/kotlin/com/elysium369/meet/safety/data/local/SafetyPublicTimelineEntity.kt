package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_public_timeline_local",
    primaryKeys = ["caseId", "milestoneId"],
    indices = [
        Index(
            value = ["caseId", "occurredAt"],
        ),
    ],
)
data class SafetyPublicTimelineEntity(
    val caseId: String,

    val milestoneId: String,

    val eventType: String,

    val publicSummary: String,

    val occurredAt: Long?,

    val recordedAt: Long,

    val sourceCount: Int,

    val evidenceCount: Int,

    val serverVersion: Long,
)
