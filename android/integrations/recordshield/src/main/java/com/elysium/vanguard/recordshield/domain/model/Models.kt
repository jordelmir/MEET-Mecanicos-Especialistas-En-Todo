package com.elysium.vanguard.recordshield.domain.model

import java.util.UUID

/**
 * ============================================================================
 * Domain Models — Core Business Entities
 * ============================================================================
 *
 * Why domain models are separate from data layer entities: Clean Architecture
 * dictates that the domain layer owns the business rules and must not depend
 * on frameworks (Room, Ktor, etc.). These models are pure Kotlin — no
 * annotations, no framework dependencies.
 * ============================================================================
 */

/** Represents a recording session with its lifecycle state. */
data class Recording(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val type: RecordingType,
    val status: RecordingStatus = RecordingStatus.RECORDING,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalChunks: Int = 0,
    val totalSizeBytes: Long = 0L
)

/** Represents a single evidence fragment (5-10 seconds of captured media). */
data class EvidenceChunk(
    val id: String = UUID.randomUUID().toString(),
    val recordingId: String,
    val chunkIndex: Int,
    val localPath: String,
    val storagePath: String? = null, // null until uploaded
    val sizeBytes: Long,
    val durationMs: Int,
    val mimeType: String = "video/mp4",
    val sha256Hash: String,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RecordingType(val value: String) {
    AUDIO("audio"),
    VIDEO("video")
}

enum class RecordingStatus(val value: String) {
    RECORDING("recording"),
    COMPLETED("completed"),
    INTERRUPTED("interrupted"),
    UPLOADING("uploading")
}

enum class UploadStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}
