package com.elysium.vanguard.recordshield.data.cloud

/**
 * ============================================================================
 * CloudStorageProvider — Abstraction for Cloud Upload Backends
 * ============================================================================
 *
 * Why: The app needs to upload evidence chunks to multiple cloud providers
 * (Google Drive, Supabase, etc.). This interface abstracts the upload logic
 * so the UploadWorker doesn't care which cloud is being used.
 *
 * Each implementation handles:
 *   - Authentication (OAuth2, API keys, etc.)
 *   - File upload with progress
 *   - Error handling and retry logic
 *   - Storage path management
 * ============================================================================
 */
interface CloudStorageProvider {

    /** Unique identifier for this provider (e.g., "google_drive", "supabase") */
    val providerId: String

    /** Human-readable name for UI display */
    val displayName: String

    /**
     * Check if this provider is configured and ready to upload.
     * Returns true if auth tokens/credentials are present and valid.
     */
    suspend fun isConfigured(): Boolean

    /**
     * Upload a chunk to the cloud storage.
     *
     * @param recordingId The recording session ID
     * @param chunkIndex Zero-based chunk index
     * @param chunkData Raw bytes of the chunk file
     * @param mimeType MIME type (video/mp4, audio/mp4, etc.)
     * @param sha256Hash Integrity hash of the chunk
     * @return Storage path or URL where the chunk was stored
     * @throws CloudUploadException on failure
     */
    suspend fun uploadChunk(
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String

    /**
     * Verify that a previously uploaded chunk exists and is intact.
     */
    suspend fun verifyChunk(storagePath: String): Boolean

    /**
     * Get the download URL for a chunk (for playback).
     * May return a signed URL or a direct link.
     */
    suspend fun getChunkDownloadUrl(storagePath: String): String?

    /**
     * List all chunks for a recording (for reconstruction).
     */
    suspend fun listChunks(recordingId: String): List<CloudChunkInfo>

    /**
     * Delete a chunk from cloud storage.
     */
    suspend fun deleteChunk(storagePath: String): Boolean

    /**
     * Get storage usage in bytes for this provider.
     */
    suspend fun getStorageUsage(): Long

    /**
     * Refresh authentication tokens if needed.
     */
    suspend fun refreshAuth(): Boolean
}

data class CloudChunkInfo(
    val storagePath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256Hash: String,
    val uploadedAt: Long
)

class CloudUploadException(
    message: String,
    val providerId: String,
    val isRetryable: Boolean = true
) : Exception(message)
