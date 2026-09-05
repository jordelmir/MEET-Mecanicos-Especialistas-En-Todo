package com.elysium369.meet.ride.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elysium369.meet.communications.DeviceMessageCipher
import com.elysium369.meet.communications.LocalCipherPayload
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class RideLocationBreadcrumb(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val headingDegrees: Int?,
    val speedMetersPerSecond: Float?,
    val capturedAtEpochMs: Long,
)

/** Durable, encrypted-at-rest delivery of exact trip location samples. */
@HiltWorker
class RideLocationBreadcrumbWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val cipher: DeviceMessageCipher,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val sequence = inputData.getLong(KEY_SEQUENCE, -1L).takeIf { it > 0L }
            ?: return Result.failure()
        val ciphertext = inputData.getString(KEY_CIPHERTEXT) ?: return Result.failure()
        val nonce = inputData.getString(KEY_NONCE) ?: return Result.failure()
        if (SupabaseModule.client.auth.currentUserOrNull() == null) return Result.retry()

        val sample = runCatching {
            val plaintext = cipher.decrypt(
                LocalCipherPayload(ciphertextBase64 = ciphertext, nonceBase64 = nonce),
                associatedData(tripId, sequence),
            )
            JSON.decodeFromString<RideLocationBreadcrumb>(plaintext)
        }.getOrElse { return Result.failure() }

        if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0 ||
            sample.accuracyMeters !in 0f..500f || sample.capturedAtEpochMs <= 0L
        ) return Result.failure()

        return runCatching {
            SupabaseModule.client.postgrest.rpc(
                "ride_record_location_breadcrumb_v2",
                buildJsonObject {
                    put("p_trip_id", tripId)
                    put("p_seq", sequence)
                    put("p_latitude", sample.latitude)
                    put("p_longitude", sample.longitude)
                    put("p_accuracy", sample.accuracyMeters)
                    sample.headingDegrees?.let { put("p_heading", it) }
                    sample.speedMetersPerSecond?.let { put("p_speed", it) }
                    put("p_captured_at_epoch_ms", sample.capturedAtEpochMs)
                },
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        private const val KEY_TRIP_ID = "trip_id"
        private const val KEY_SEQUENCE = "sequence"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_NONCE = "nonce"
        private const val MAX_ATTEMPTS = 12
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = false }

        fun enqueue(
            context: Context,
            cipher: DeviceMessageCipher,
            tripId: String,
            sequence: Long,
            sample: RideLocationBreadcrumb,
        ) {
            val encrypted = cipher.encrypt(
                JSON.encodeToString(RideLocationBreadcrumb.serializer(), sample),
                associatedData(tripId, sequence),
            )
            val request = OneTimeWorkRequestBuilder<RideLocationBreadcrumbWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TRIP_ID to tripId,
                        KEY_SEQUENCE to sequence,
                        KEY_CIPHERTEXT to encrypted.ciphertextBase64,
                        KEY_NONCE to encrypted.nonceBase64,
                    ),
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ride_location_${tripId}_seq${sequence}",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        private fun associatedData(tripId: String, sequence: Long) =
            "meet-rides-location-v1|$tripId|$sequence"
    }
}
