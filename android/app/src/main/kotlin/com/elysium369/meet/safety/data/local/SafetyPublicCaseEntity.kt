package com.elysium369.meet.safety.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "safety_public_cases_local",
    indices = [
        Index("lifecycle"),
        Index("publishedAt"),
    ],
)
data class SafetyPublicCaseEntity(
    @PrimaryKey
    val caseId: String,

    val caseType: String,

    val title: String,

    val publicSummary: String,

    val lifecycle: String,

    val confidenceScore: Float,

    val eventCount: Int,
    val claimCount: Int,
    val sourceCount: Int,
    val evidenceCount: Int,

    val publishedAt: Long,
    val lastUpdatedAt: Long,

    val serverVersion: Long,
)
