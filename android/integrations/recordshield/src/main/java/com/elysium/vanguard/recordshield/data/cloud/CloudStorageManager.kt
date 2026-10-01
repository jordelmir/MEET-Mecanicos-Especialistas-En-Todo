package com.elysium.vanguard.recordshield.data.cloud

import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * CloudStorageManager — Provider Selection & Upload Orchestration
 * ============================================================================
 *
 * Why: The user chooses which cloud to upload evidence chunks to.
 * This manager routes uploads to the correct provider and handles
 * fallback logic if the primary provider fails.
 *
 * Provider priority:
 *   1. User-selected provider (stored in SecureStorage)
 *   2. First available configured provider
 *   3. Fail with error
 *
 * Supports:
 *   - Google Drive (15GB free, user-owned)
 *   - Supabase (existing implementation, requires backend)
 * ============================================================================
 */
@Singleton
class CloudStorageManager @Inject constructor(
    private val googleDrive: GoogleDriveStorageProvider,
    private val supabase: SupabaseStorageProvider,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val TAG = "CloudStorageManager"
        const val PROVIDER_GOOGLE_DRIVE = "google_drive"
        const val PROVIDER_SUPABASE = "supabase"
    }

    /**
     * Get the currently selected provider ID.
     */
    fun getSelectedProviderId(): String {
        return secureStorage.selectedCloudProvider ?: PROVIDER_GOOGLE_DRIVE
    }

    /**
     * Set the selected cloud provider.
     */
    fun setSelectedProvider(providerId: String) {
        secureStorage.selectedCloudProvider = providerId
        Log.i(TAG, "Cloud provider set to: $providerId")
    }

    /**
     * Get all available providers.
     */
    fun getAvailableProviders(): List<CloudProviderInfo> {
        return listOf(
            CloudProviderInfo(
                id = PROVIDER_GOOGLE_DRIVE,
                name = "Google Drive",
                description = "15GB free. Files stored in your personal Drive under RecordShield folder.",
                icon = "ic_google_drive",
                isConfigured = false // Will be checked asynchronously
            ),
            CloudProviderInfo(
                id = PROVIDER_SUPABASE,
                name = "Supabase Cloud",
                description = "Encrypted cloud storage via Vercel backend. Requires device registration.",
                icon = "ic_supabase",
                isConfigured = false
            )
        )
    }

    /**
     * Get the active storage provider based on user selection.
     */
    suspend fun getActiveProvider(): CloudStorageProvider {
        val selectedId = getSelectedProviderId()
        Log.i(TAG, "getActiveProvider: selected=$selectedId")

        return when (selectedId) {
            PROVIDER_GOOGLE_DRIVE -> {
                val configured = googleDrive.isConfigured()
                Log.i(TAG, "Google Drive configured: $configured")
                if (configured) {
                    googleDrive
                } else {
                    // Fallback to Supabase if Drive is not configured
                    if (supabase.isConfigured()) {
                        Log.w(TAG, "Google Drive not configured, falling back to Supabase")
                        supabase
                    } else {
                        throw CloudUploadException(
                            "No cloud provider configured. Please set up Google Drive or Supabase.",
                            "none",
                            false
                        )
                    }
                }
            }
            PROVIDER_SUPABASE -> {
                if (supabase.isConfigured()) {
                    supabase
                } else {
                    // Fallback to Google Drive if Supabase is not configured
                    if (googleDrive.isConfigured()) {
                        Log.w(TAG, "Supabase not configured, falling back to Google Drive")
                        googleDrive
                    } else {
                        throw CloudUploadException(
                            "No cloud provider configured. Please set up Google Drive or Supabase.",
                            "none",
                            false
                        )
                    }
                }
            }
            else -> {
                throw CloudUploadException(
                    "Unknown provider: $selectedId",
                    "none",
                    false
                )
            }
        }
    }

    /**
     * Upload a chunk using the active provider.
     */
    suspend fun uploadChunk(
        recordingId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String {
        val provider = getActiveProvider()

        return try {
            provider.uploadChunk(recordingId, chunkIndex, chunkData, mimeType, sha256Hash)
        } catch (e: CloudUploadException) {
            if (e.isRetryable) {
                Log.w(TAG, "Upload failed with ${provider.providerId}, attempting fallback...")
                // Try the other provider
                val fallback = when (provider.providerId) {
                    PROVIDER_GOOGLE_DRIVE -> if (supabase.isConfigured()) supabase else null
                    PROVIDER_SUPABASE -> if (googleDrive.isConfigured()) googleDrive else null
                    else -> null
                }

                if (fallback != null) {
                    Log.i(TAG, "Falling back to ${fallback.providerId}")
                    fallback.uploadChunk(recordingId, chunkIndex, chunkData, mimeType, sha256Hash)
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    /**
     * Check if any provider is configured and ready.
     */
    suspend fun isAnyProviderReady(): Boolean {
        return try {
            getActiveProvider()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get storage usage from the active provider.
     */
    suspend fun getStorageUsage(): Long {
        return try {
            getActiveProvider().getStorageUsage()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Upload a file with a custom name to a specific folder.
     * Used for dual camera uploads (rear.mp4, front.mp4 in same chunk folder).
     */
    suspend fun uploadFileToFolder(
        recordingId: String,
        chunkIndex: Int,
        fileName: String,
        fileData: ByteArray,
        mimeType: String,
        sha256Hash: String
    ): String {
        val provider = getActiveProvider()
        return when (provider) {
            is GoogleDriveStorageProvider -> {
                provider.uploadFileWithCustomName(recordingId, chunkIndex, fileName, fileData, mimeType, sha256Hash)
            }
            else -> {
                // For other providers, use standard upload
                provider.uploadChunk(recordingId, chunkIndex, fileData, mimeType, sha256Hash)
            }
        }
    }
}

data class CloudProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val isConfigured: Boolean
)
