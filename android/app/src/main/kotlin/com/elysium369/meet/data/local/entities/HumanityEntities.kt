package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "humanity_learning_progress_local")
data class HumanityProgressEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val targetType: String, // KNOWLEDGE_NODE, SKILL, MISSION
    val targetId: String,
    val status: String,
    val repetitionsCount: Int = 0,
    val intervalDays: Double = 1.0,
    val easeFactor: Double = 2.5,
    val nextReviewEpochMs: Long = 0L,
    val lastReviewedEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "humanity_evidence_items_local")
data class HumanityEvidenceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val skillId: String,
    val missionId: String?,
    val evidenceType: String,
    val executionTruth: String,
    val evidencePayloadHash: String,
    val metadataJson: String = "{}",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)

@Entity(tableName = "humanity_capability_records_local")
data class HumanityCapabilityEntity(
    @PrimaryKey val skillId: String,
    val userId: String,
    val currentLevel: String,
    val demonstratedEvidenceCount: Int = 0,
    val lastDemonstratedEpochMs: Long = System.currentTimeMillis(),
    val verifiedByExpert: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
