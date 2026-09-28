package com.elysium.vanguard.recordshield.domain.repository

import com.elysium.vanguard.recordshield.domain.model.EvidenceChunk
import com.elysium.vanguard.recordshield.domain.model.Recording
import com.elysium.vanguard.recordshield.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow

/**
 * ============================================================================
 * Repository Interfaces — Domain Layer Contracts
 * ============================================================================
 *
 * Why interfaces here: The domain layer defines WHAT operations are needed,
 * not HOW they're implemented. The data layer provides the concrete
 * implementations (Room for local, Ktor for remote). This enables:
 * - Testability: Mock repositories in unit tests
 * - Flexibility: Swap implementations without touching business logic
 * - Dependency inversion: Domain never depends on frameworks
 * ============================================================================
 */

interface RecordingRepository {
    /** Create a new recording session and return its ID. */
    suspend fun createRecording(recording: Recording): String

    /** Get a recording by ID. */
    suspend fun getRecording(id: String): Recording?

    /** Update recording status (e.g., RECORDING → COMPLETED). */
    suspend fun updateRecordingStatus(id: String, status: String, endedAt: Long? = null)

    /** Observe all recordings, ordered by most recent. */
    fun observeAllRecordings(): Flow<List<Recording>>

    /** Delete a recording and all its chunks from local storage. */
    suspend fun deleteRecording(id: String)
}

interface ChunkRepository {
    /** Insert a new chunk record into local database. */
    suspend fun insertChunk(chunk: EvidenceChunk)

    /** Get all chunks for a recording, ordered by index. */
    suspend fun getChunksForRecording(recordingId: String): List<EvidenceChunk>

    /** Get all chunks pending upload. */
    suspend fun getPendingChunks(): List<EvidenceChunk>

    /** Update chunk upload status after successful/failed upload. */
    suspend fun updateChunkUploadStatus(chunkId: String, status: UploadStatus, storagePath: String? = null)

    /** Observe pending upload count for progress UI. */
    fun observePendingCount(): Flow<Int>
}

interface EvidenceUploadRepository {
    /**
     * Upload a single chunk to the Vercel endpoint.
     * Returns the storage path on success, throws on failure.
     */
    suspend fun uploadChunk(
        deviceId: String,
        deviceToken: String,
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        sha256Hash: String,
        mimeType: String,
        durationMs: Int
    ): String

    /**
     * Create a recording session on the remote server.
     * Returns the remote recording ID.
     */
    suspend fun createRemoteRecording(
        deviceId: String,
        deviceToken: String,
        recordingType: String
    ): String
}
