package com.elysium.vanguard.recordshield.service

import android.content.Context
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.elysium.vanguard.recordshield.data.cloud.CloudStorageManager
import com.elysium.vanguard.recordshield.data.cloud.CloudUploadException
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import com.elysium.vanguard.recordshield.domain.model.UploadStatus
import com.elysium.vanguard.recordshield.domain.repository.ChunkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ============================================================================
 * UploadWorker — Cloud-Agnostic Evidence Upload via WorkManager
 * ============================================================================
 *
 * Why WorkManager:
 *   - Survives process death (coroutines don't)
 *   - Resumes after device reboot
 *   - Applies exponential backoff automatically
 *   - Respects network constraints
 *   - Works within Android's battery optimization framework
 *
 * Cloud Provider Support:
 *   - Google Drive (primary, user-selected)
 *   - Supabase (fallback)
 *   - Automatic failover between providers
 *
 * Upload Flow:
 *   1. Query Room for pending chunks
 *   2. For each chunk: read file → compute hash → upload via CloudStorageManager
 *   3. Mark as UPLOADED on success, FAILED on error
 *   4. Delete local file ONLY after successful upload (evidence preservation)
 * ============================================================================
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chunkRepository: ChunkRepository,
    private val cloudStorageManager: CloudStorageManager,
    private val secureStorage: SecureStorage
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        private const val WORK_NAME_PERIODIC = "evidence_upload_periodic"
        private const val WORK_NAME_IMMEDIATE = "evidence_upload_immediate"

        // Observable upload success events for UI confirmation
        private val _uploadSuccessEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 10)
        val uploadSuccessEvents: kotlinx.coroutines.flow.SharedFlow<String> = _uploadSuccessEvents.asSharedFlow()

        fun emitUploadSuccess(message: String) {
            _uploadSuccessEvents.tryEmit(message)
        }

        /**
         * Enqueue an immediate one-time upload attempt.
         * Called after each chunk is created during recording.
         */
        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .addTag("immediate_upload")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME_IMMEDIATE,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }

        /**
         * Schedule periodic upload sweep every 15 minutes.
         * Catches any chunks that the immediate worker missed.
         */
        fun schedulePeriodicUpload(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UploadWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    60, TimeUnit.SECONDS
                )
                .addTag("periodic_upload")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        /**
         * Cancel all pending upload work.
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_IMMEDIATE)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        Log.i(TAG, "=== UploadWorker doWork() START ===")

        // Check if a cloud provider is configured
        val providerReady = cloudStorageManager.isAnyProviderReady()
        Log.i(TAG, "Cloud provider ready: $providerReady")

        if (!providerReady) {
            Log.w(TAG, "No cloud provider configured — skipping upload")
            return@withContext Result.success()
        }

        val pendingChunks = chunkRepository.getPendingChunks()
        Log.i(TAG, "Pending chunks found: ${pendingChunks.size}")

        if (pendingChunks.isEmpty()) {
            Log.i(TAG, "No pending chunks to upload")
            return@withContext Result.success()
        }

        Log.i(TAG, "Found ${pendingChunks.size} pending chunks to upload")

        var failedCount = 0
        var uploadedCount = 0

        for (chunk in pendingChunks) {
            try {
                Log.i(TAG, "Processing chunk ${chunk.chunkIndex} (id=${chunk.id}, path=${chunk.localPath})")
                // Mark as UPLOADING
                chunkRepository.updateChunkUploadStatus(chunk.id, UploadStatus.UPLOADING)

                // Read chunk file
                val file = File(chunk.localPath)
                if (!file.exists()) {
                    Log.w(TAG, "Chunk file missing: ${chunk.localPath}")
                    chunkRepository.updateChunkUploadStatus(chunk.id, UploadStatus.FAILED)
                    failedCount++
                    continue
                }

                val chunkData = file.readBytes()

                // Upload via CloudStorageManager (routes to active provider)
                val storagePath = cloudStorageManager.uploadChunk(
                    recordingId = chunk.recordingId,
                    chunkIndex = chunk.chunkIndex,
                    chunkData = chunkData,
                    mimeType = chunk.mimeType,
                    sha256Hash = chunk.sha256Hash
                )

                // Mark as UPLOADED
                chunkRepository.updateChunkUploadStatus(
                    chunk.id,
                    UploadStatus.UPLOADED,
                    storagePath
                )

                // CRITICAL: Only delete local file AFTER successful upload
                if (file.delete()) {
                    Log.i(TAG, "Chunk uploaded — local file purged")
                } else {
                    Log.w(TAG, "Chunk uploaded but could not delete local file")
                }

                uploadedCount++
                _uploadSuccessEvents.tryEmit("Chunk ${chunk.chunkIndex} uploaded to Drive")
                Log.i(TAG, "Chunk ${chunk.chunkIndex} uploaded successfully ($uploadedCount/${pendingChunks.size})")

            } catch (e: CloudUploadException) {
                Log.e(TAG, "Cloud upload FAILED for chunk ${chunk.chunkIndex}: ${e.message}")
                chunkRepository.updateChunkUploadStatus(chunk.id, UploadStatus.FAILED)
                failedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during upload for chunk ${chunk.chunkIndex}", e)
                chunkRepository.updateChunkUploadStatus(chunk.id, UploadStatus.FAILED)
                failedCount++
            }
        }

        Log.i(TAG, "Upload complete: $uploadedCount succeeded, $failedCount failed")

        if (failedCount > 0) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
