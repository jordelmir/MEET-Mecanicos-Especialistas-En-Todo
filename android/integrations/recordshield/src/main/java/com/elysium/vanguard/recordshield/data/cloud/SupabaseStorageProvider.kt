package com.elysium.vanguard.recordshield.data.cloud

import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import com.elysium.vanguard.recordshield.data.remote.EvidenceApiClient
import com.elysium.vanguard.recordshield.data.remote.ChunkMetadataRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * SupabaseStorageProvider — Supabase Implementation
 * ============================================================================
 *
 * Preserves the existing upload pipeline:
 *   1. Request signed URL from Vercel middleware
 *   2. Upload directly to Supabase Storage via signed URL
 *   3. Register chunk metadata via Vercel
 *
 * This provider is used when the user selects Supabase as their cloud backend,
 * or as a fallback when Google Drive is unavailable.
 * ============================================================================
 */
@Singleton
class SupabaseStorageProvider @Inject constructor(
    private val apiClient: EvidenceApiClient,
    private val secureStorage: SecureStorage
) : CloudStorageProvider {

    companion object {
        private const val TAG = "SupabaseProvider"
    }

    override val providerId = "supabase"
    override val displayName = "Supabase Cloud"

    override suspend fun isConfigured(): Boolean {
        val deviceId = secureStorage.deviceId
        val deviceToken = secureStorage.deviceToken
        val baseUrl = secureStorage.apiBaseUrl
        return deviceId != null && deviceToken != null && baseUrl != "https://your-project.vercel.app"
    }

    override suspend fun uploadChunk(
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String {
        val deviceId = secureStorage.deviceId
            ?: throw CloudUploadException("Device not registered", providerId, false)
        val deviceToken = secureStorage.deviceToken
            ?: throw CloudUploadException("Device token missing", providerId, false)

        // Step 1: Get signed URL from Vercel
        val signedUrlResponse = apiClient.getUploadUrl(
            deviceId = deviceId,
            deviceToken = deviceToken,
            recordingId = recordingId,
            chunkIndex = chunkIndex,
            mimeType = mimeType
        )

        // Step 2: Upload directly to Supabase Storage
        apiClient.uploadToSignedUrl(
            signedUrl = signedUrlResponse.signedUrl,
            chunkData = chunkData,
            mimeType = mimeType
        )

        // Step 3: Register metadata via Vercel
        apiClient.registerChunk(
            deviceId = deviceId,
            deviceToken = deviceToken,
            metadata = ChunkMetadataRequest(
                recordingId = recordingId,
                chunkIndex = chunkIndex,
                storagePath = signedUrlResponse.path,
                sizeBytes = chunkData.size.toLong(),
                durationMs = 0,
                mimeType = mimeType,
                sha256Hash = sha256Hash
            )
        )

        Log.i(TAG, "Chunk $chunkIndex uploaded to Supabase for recording $recordingId")
        return signedUrlResponse.path
    }

    override suspend fun verifyChunk(storagePath: String): Boolean {
        // Supabase verification would require a download check
        // For now, assume uploaded chunks are intact
        return true
    }

    override suspend fun getChunkDownloadUrl(storagePath: String): String? {
        // Would need to generate a signed read URL from Supabase
        // For now, return null (chunks should be played from local cache)
        return null
    }

    override suspend fun listChunks(recordingId: String): List<CloudChunkInfo> {
        // Would need Supabase query — not implemented yet
        return emptyList()
    }

    override suspend fun deleteChunk(storagePath: String): Boolean {
        // Would need Supabase storage delete — not implemented yet
        return false
    }

    override suspend fun getStorageUsage(): Long {
        // Would need Supabase query — not implemented yet
        return 0L
    }

    override suspend fun refreshAuth(): Boolean {
        // Supabase tokens don't expire (device registration based)
        return true
    }
}
