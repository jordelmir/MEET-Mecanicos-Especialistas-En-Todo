package com.elysium.vanguard.recordshield.data.cloud

import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * GoogleDriveStorageProvider — Google Drive Implementation
 * ============================================================================
 *
 * Upload strategy:
 *   1. Get/create root folder "RecordShield" on user's Drive
 *   2. Get/create subfolder per recording session
 *   3. Upload chunk as file in that subfolder
 *   4. Return Google Drive file ID as storage path
 *
 * Why Google Drive:
 *   - 15GB free storage per Google account
 *   - No server-side infrastructure needed
 *   - User owns the data directly
 *   - OAuth2 ensures secure, revocable access
 *   - Works offline with local cache, syncs when online
 * ============================================================================
 */
@Singleton
class GoogleDriveStorageProvider @Inject constructor(
    private val driveClient: GoogleDriveClient,
    private val secureStorage: SecureStorage
) : CloudStorageProvider {

    companion object {
        private const val TAG = "GDriveProvider"
        // Cached folder IDs to avoid repeated API calls
        private var cachedRootFolderId: String? = null
        private var cachedRecordingFolderIds = mutableMapOf<String, String>()
    }

    override val providerId = "google_drive"
    override val displayName = "Google Drive"

    override suspend fun isConfigured(): Boolean {
        val hasToken = secureStorage.googleDriveAccessToken != null
        Log.d(TAG, "isConfigured: hasToken=$hasToken")
        return hasToken
    }

    override suspend fun uploadChunk(
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String {
        Log.e(TAG, "=== uploadChunk START: recording=$recordingId, chunk=$chunkIndex, size=${chunkData.size}, mime=$mimeType")
        try {
            // Step 1: Get or create root folder
            Log.d(TAG, "Step 1: Getting root folder (cached=$cachedRootFolderId)")
            val rootFolderId = cachedRootFolderId ?: run {
                val id = driveClient.getOrCreateRootFolder()
                Log.i(TAG, "Root folder ID: $id")
                cachedRootFolderId = id
                id
            }

            // Step 2: Get or create recording subfolder
            Log.d(TAG, "Step 2: Getting recording folder (cached=${cachedRecordingFolderIds[recordingId]})")
            val folderId = cachedRecordingFolderIds[recordingId] ?: run {
                val id = driveClient.getOrCreateRecordingFolder(rootFolderId, recordingId)
                Log.i(TAG, "Recording folder ID: $id")
                cachedRecordingFolderIds[recordingId] = id
                id
            }

            // Step 3: Upload chunk file
            Log.i(TAG, "Step 3: Uploading file to folder $folderId")
            val fileId = driveClient.uploadChunkFile(
                folderId = folderId,
                chunkIndex = chunkIndex,
                chunkData = chunkData,
                mimeType = mimeType
            )

            Log.e(TAG, "=== uploadChunk DONE: chunk $chunkIndex → Drive fileID=$fileId")
            return "drive://$fileId"

        } catch (e: CloudUploadException) {
            Log.e(TAG, "=== uploadChunk CloudUploadException: ${e.message}", e)
            if (e.message?.contains("401") == true || e.message?.contains("token") == true) {
                if (driveClient.refreshAccessToken()) {
                    Log.i(TAG, "Token refreshed, retrying upload...")
                    return uploadChunk(recordingId, chunkIndex, chunkData, mimeType, sha256Hash)
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "=== uploadChunk unexpected exception: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    override suspend fun verifyChunk(storagePath: String): Boolean {
        val fileId = storagePath.removePrefix("drive://")
        return driveClient.verifyFileExists(fileId)
    }

    override suspend fun getChunkDownloadUrl(storagePath: String): String? {
        val fileId = storagePath.removePrefix("drive://")
        return try {
            driveClient.getDownloadUrl(fileId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get download URL for $storagePath", e)
            null
        }
    }

    override suspend fun listChunks(recordingId: String): List<CloudChunkInfo> {
        val rootFolderId = cachedRootFolderId ?: return emptyList()
        val folderId = cachedRecordingFolderIds[recordingId] ?: return emptyList()
        return driveClient.listFilesInFolder(folderId)
    }

    override suspend fun deleteChunk(storagePath: String): Boolean {
        val fileId = storagePath.removePrefix("drive://")
        return driveClient.deleteFile(fileId)
    }

    override suspend fun getStorageUsage(): Long {
        return driveClient.getStorageUsage()
    }

    override suspend fun refreshAuth(): Boolean {
        return driveClient.refreshAccessToken()
    }

    /**
     * Clear cached folder IDs (e.g., on logout).
     */
    fun clearCache() {
        cachedRootFolderId = null
        cachedRecordingFolderIds.clear()
    }

    /**
     * Upload a file with a custom name to a recording folder.
     * Used for dual camera uploads (rear.mp4, front.mp4).
     */
    suspend fun uploadFileWithCustomName(
        recordingId: String,
        chunkIndex: Int,
        fileName: String,
        fileData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String {
        Log.e(TAG, "=== uploadFileWithCustomName START: recording=$recordingId, file=$fileName, size=${fileData.size}")
        try {
            // Get or create root folder
            val rootFolderId = cachedRootFolderId ?: run {
                val id = driveClient.getOrCreateRootFolder()
                cachedRootFolderId = id
                id
            }

            // Get or create recording subfolder
            val recordingFolderId = cachedRecordingFolderIds[recordingId] ?: run {
                val id = driveClient.getOrCreateRecordingFolder(rootFolderId, recordingId)
                cachedRecordingFolderIds[recordingId] = id
                id
            }

            // Create or get chunk subfolder (chunk_XXXXX/)
            val chunkFolderName = "chunk_${String.format("%05d", chunkIndex)}"
            val chunkFolderId = driveClient.getOrCreateSubfolder(recordingFolderId, chunkFolderName)

            // Upload file with custom name
            val fileId = driveClient.uploadFileToFolder(
                folderId = chunkFolderId,
                fileName = fileName,
                fileData = fileData,
                mimeType = mimeType
            )

            Log.e(TAG, "=== uploadFileWithCustomName DONE: $fileName → Drive fileID=$fileId")
            return "drive://$fileId"

        } catch (e: CloudUploadException) {
            Log.e(TAG, "=== uploadFileWithCustomName CloudUploadException: ${e.message}", e)
            if (e.message?.contains("401") == true || e.message?.contains("token") == true) {
                if (driveClient.refreshAccessToken()) {
                    return uploadFileWithCustomName(recordingId, chunkIndex, fileName, fileData, mimeType, sha256Hash)
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "=== uploadFileWithCustomName unexpected exception: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }
}
