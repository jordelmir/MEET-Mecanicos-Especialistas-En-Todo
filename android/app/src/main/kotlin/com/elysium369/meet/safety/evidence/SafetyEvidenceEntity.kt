package com.elysium369.meet.safety.evidence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local upload intent. Only serverReceipt represents remote custody acknowledgement. */
@Entity(tableName = "safety_evidence_local", indices = [Index(value = ["ownerUserId", "reportId"]), Index(value = ["uploadState"])])
data class SafetyEvidenceEntity(
    @PrimaryKey val evidenceId: String,
    val reportId: String,
    val ownerUserId: String,
    val encryptedPath: String,
    val contentSha256: String,
    val mimeType: String,
    val byteCount: Long,
    val stagedAt: Long,
    val uploadState: String = "STAGED",
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val serverReceipt: String? = null,
)
