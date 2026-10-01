package com.elysium.vanguard.recordshield.data.repository

import com.elysium.vanguard.recordshield.data.local.ChunkDao
import com.elysium.vanguard.recordshield.data.local.ChunkEntity
import com.elysium.vanguard.recordshield.data.local.RecordingDao
import com.elysium.vanguard.recordshield.data.local.RecordingEntity
import com.elysium.vanguard.recordshield.data.remote.EvidenceApiClient
import com.elysium.vanguard.recordshield.domain.model.*
import com.elysium.vanguard.recordshield.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * Repository Implementations — Data Layer
 * ============================================================================
 *
 * Why @Singleton: Repositories manage database connections and HTTP clients.
 * Creating multiple instances wastes resources and can cause threading issues
 * with Room's WAL mode. A single instance per repository is the standard.
 *
 * These classes bridge domain models ↔ data layer entities (Room/Ktor).
 * ============================================================================
 */

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val recordingDao: RecordingDao
) : RecordingRepository {

    override suspend fun createRecording(recording: Recording): String {
        recordingDao.insert(recording.toEntity())
        return recording.id
    }

    override suspend fun getRecording(id: String): Recording? {
        return recordingDao.getById(id)?.toDomain()
    }

    override suspend fun updateRecordingStatus(id: String, status: String, endedAt: Long?) {
        recordingDao.updateStatus(id, status, endedAt)
    }

    override fun observeAllRecordings(): Flow<List<Recording>> {
        return recordingDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun deleteRecording(id: String) {
        recordingDao.delete(id)
    }
}

@Singleton
class ChunkRepositoryImpl @Inject constructor(
    private val chunkDao: ChunkDao,
    private val recordingDao: RecordingDao
) : ChunkRepository {

    override suspend fun insertChunk(chunk: EvidenceChunk) {
        chunkDao.insert(chunk.toEntity())
        // Atomically update recording counters
        recordingDao.incrementChunkCounters(chunk.recordingId, chunk.sizeBytes)
    }

    override suspend fun getChunksForRecording(recordingId: String): List<EvidenceChunk> {
        return chunkDao.getChunksForRecording(recordingId).map { it.toDomain() }
    }

    override suspend fun getPendingChunks(): List<EvidenceChunk> {
        return chunkDao.getPendingChunks().map { it.toDomain() }
    }

    override suspend fun updateChunkUploadStatus(
        chunkId: String,
        status: UploadStatus,
        storagePath: String?
    ) {
        chunkDao.updateUploadStatus(chunkId, status.name, storagePath)
    }

    override fun observePendingCount(): Flow<Int> {
        return chunkDao.observePendingCount()
    }
}

@Singleton
class EvidenceUploadRepositoryImpl @Inject constructor(
    private val apiClient: EvidenceApiClient
) : EvidenceUploadRepository {

    override suspend fun uploadChunk(
        deviceId: String,
        deviceToken: String,
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        sha256Hash: String,
        mimeType: String,
        durationMs: Int
    ): String {
        // Phase 6: Top 1% Architecture - Secure 2-Step Direct Upload

        // 1. Ask Vercel (The Guard) for a Signed URL
        val signedUrlResponse = apiClient.getUploadUrl(
            deviceId = deviceId,
            deviceToken = deviceToken,
            recordingId = recordingId,
            chunkIndex = chunkIndex,
            mimeType = mimeType
        )

        // 2. Upload DIRECTLY to Supabase (Bypassing Vercel limits)
        apiClient.uploadToSignedUrl(
            signedUrl = signedUrlResponse.signedUrl,
            chunkData = chunkData,
            mimeType = mimeType
        )

        // 3. Register Chunk in the DB via Vercel (Audit Log)
        val registerResponse = apiClient.registerChunk(
            deviceId = deviceId,
            deviceToken = deviceToken,
            metadata = com.elysium.vanguard.recordshield.data.remote.ChunkMetadataRequest(
                recordingId = recordingId,
                chunkIndex = chunkIndex,
                storagePath = signedUrlResponse.path,
                sizeBytes = chunkData.size.toLong(),
                durationMs = durationMs,
                mimeType = mimeType,
                sha256Hash = sha256Hash
            )
        )

        return signedUrlResponse.path
    }

    override suspend fun createRemoteRecording(
        deviceId: String,
        deviceToken: String,
        recordingType: String
    ): String {
        return apiClient.createRemoteRecording(deviceId, deviceToken, recordingType)
    }
}

// ============================================================================
// MAPPERS — Entity ↔ Domain conversions
// ============================================================================

private fun Recording.toEntity() = RecordingEntity(
    id = id,
    deviceId = deviceId,
    recordingType = type.value,
    status = status.value,
    startedAt = startedAt,
    endedAt = endedAt,
    totalChunks = totalChunks,
    totalSizeBytes = totalSizeBytes
)

private fun RecordingEntity.toDomain() = Recording(
    id = id,
    deviceId = deviceId,
    type = RecordingType.entries.firstOrNull { it.value.equals(recordingType, ignoreCase = true) } ?: RecordingType.VIDEO,
    status = RecordingStatus.entries.firstOrNull { it.value.equals(status, ignoreCase = true) } ?: RecordingStatus.COMPLETED,
    startedAt = startedAt,
    endedAt = endedAt,
    totalChunks = totalChunks,
    totalSizeBytes = totalSizeBytes
)

private fun EvidenceChunk.toEntity() = ChunkEntity(
    id = id,
    recordingId = recordingId,
    chunkIndex = chunkIndex,
    localPath = localPath,
    storagePath = storagePath,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    mimeType = mimeType,
    sha256Hash = sha256Hash,
    uploadStatus = uploadStatus.name,
    createdAt = createdAt
)

private fun ChunkEntity.toDomain() = EvidenceChunk(
    id = id,
    recordingId = recordingId,
    chunkIndex = chunkIndex,
    localPath = localPath,
    storagePath = storagePath,
    sizeBytes = sizeBytes,
    durationMs = durationMs,
    mimeType = mimeType,
    sha256Hash = sha256Hash,
    uploadStatus = runCatching { UploadStatus.valueOf(uploadStatus) }.getOrDefault(UploadStatus.PENDING),
    createdAt = createdAt
)
