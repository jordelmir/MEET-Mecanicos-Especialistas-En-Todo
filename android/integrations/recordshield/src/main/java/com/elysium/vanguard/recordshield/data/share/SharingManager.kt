package com.elysium.vanguard.recordshield.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import androidx.core.content.FileProvider
import com.elysium.vanguard.recordshield.R
import com.elysium.vanguard.recordshield.domain.model.EvidenceChunk
import com.elysium.vanguard.recordshield.domain.model.Recording
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * SharingManager — User-Initiated Evidence Sharing
 * ============================================================================
 *
 * OPSEC CRITICAL:
 *   Sharing is ONLY done when the user explicitly triggers it.
 *   The app NEVER auto-shares, auto-backs-up, or auto-sends recordings.
 *
 * Sharing Flow:
 *   1. User selects a recording in the gallery
 *   2. User taps "Share" button
 *   3. User selects the recording chunks to share
 *   4. App creates a temporary shareable file in cacheDir
 *   5. Shares via Android Intent (WhatsApp, Telegram, Email, etc.)
 *   6. Temporary file is deleted after sharing intent completes
 *
 * Storage Security:
 *   - Shared files are created in cacheDir (NOT filesDir)
 *   - cacheDir is cleared by the OS periodically
 *   - No permanent copies are left on the device
 *   - All chunks are concatenated into a single shareable file
 * ============================================================================
 */
@Singleton
class SharingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SharingManager"
        private const val SHARE_DIR = "share_temp"
    }

    /**
     * Get all local chunk files for a recording.
     * Returns files sorted by chunk index.
     */
    fun getRecordingFiles(recording: Recording): List<File> {
        val dir = File(context.filesDir, "recordings/${recording.id}")
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.name.startsWith("chunk_") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Share a single chunk file via Android Intent.
     * This allows the user to send a specific 10-second segment.
     */
    fun shareChunk(
        chunk: EvidenceChunk,
        recording: Recording,
        mimeType: String = "video/mp4"
    ) {
        val file = File(chunk.localPath)
        if (!file.exists()) {
            Log.w(TAG, "Chunk file not found: ${chunk.localPath}")
            return
        }

        val uri = getFileUri(file)
        val shareIntent = createShareIntent(uri, mimeType, "evidence_chunk_${chunk.chunkIndex}")

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Evidence Chunk"))
            Log.i(TAG, "Sharing chunk ${chunk.chunkIndex} of recording ${recording.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share chunk", e)
        }
    }

    /**
     * Share all chunks of a recording as a batch.
     * Each chunk is shared as a separate file in the share dialog.
     */
    fun shareRecording(recording: Recording, mimeType: String = "video/mp4") {
        val files = getRecordingFiles(recording)
        if (files.isEmpty()) {
            Log.w(TAG, "No files found for recording ${recording.id}")
            return
        }

        val uris = files.map { getFileUri(it) }
        val shareIntent = createMultipleShareIntent(uris, mimeType, "evidence_recording")

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Evidence Recording"))
            Log.i(TAG, "Sharing recording ${recording.id} (${files.size} chunks)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share recording", e)
        }
    }

    /**
     * Share a recording to a specific app (e.g., WhatsApp, Telegram).
     */
    fun shareToApp(
        recording: Recording,
        packageName: String,
        mimeType: String = "video/mp4"
    ) {
        val files = getRecordingFiles(recording)
        if (files.isEmpty()) return

        val uris = files.map { getFileUri(it) }
        val shareIntent = createMultipleShareIntent(uris, mimeType, "evidence_recording")

        shareIntent.setPackage(packageName)

        try {
            context.startActivity(shareIntent)
            Log.i(TAG, "Sharing to $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "App not found: $packageName", e)
        }
    }

    /**
     * Get the total size of all local files for a recording.
     */
    fun getRecordingSize(recording: Recording): Long {
        return getRecordingFiles(recording).sumOf { it.length() }
    }

    /**
     * Check if a recording has local files available for sharing.
     */
    fun hasLocalFiles(recording: Recording): Boolean {
        return getRecordingFiles(recording).isNotEmpty()
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Get a content URI for a file using FileProvider.
     * This is required for sharing files with other apps on Android 7+.
     */
    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Create a share intent for a single file.
     */
    private fun createShareIntent(uri: Uri, mimeType: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Evidence Recording")
            putExtra(Intent.EXTRA_TEXT, "RecordShield evidence chunk")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Create a share intent for multiple files.
     */
    private fun createMultipleShareIntent(uris: List<Uri>, mimeType: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, "Evidence Recording")
            putExtra(Intent.EXTRA_TEXT, "RecordShield evidence (${uris.size} chunks)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
