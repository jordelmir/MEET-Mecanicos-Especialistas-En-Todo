package com.elysium369.meet.ride.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.data.remote.RideDriverEnrollmentGateway
import com.elysium369.meet.ride.data.remote.RideDriverEnrollmentResult
import com.elysium369.meet.ride.data.remote.RideDriverPilotEnrollment
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.ServiceVerificationSubmission
import com.elysium369.meet.ride.data.remote.TrustEvidenceFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import java.util.concurrent.TimeUnit

@HiltWorker
class RideDriverEnrollmentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val gateway: RideDriverEnrollmentGateway,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val enrollment = inputData.toEnrollment() ?: return Result.failure(
            workDataOf("error_code" to "INVALID_LOCAL_ENROLLMENT"),
        )
        val sessionUserId = SupabaseModule.client.auth.currentUserOrNull()?.id
            ?: return Result.retry()
        val idempotencyKey =
            "enroll:$sessionUserId:${enrollment.evidenceManifestSha256.take(32)}"
        return when (val result = gateway.enroll(enrollment, idempotencyKey)) {
            is RideDriverEnrollmentResult.Accepted -> {
                // Enrollment RPC succeeded. Now try to submit evidence.
                // Even if evidence upload fails, we save the enrollment locally
                // so the app knows the driver was registered (without evidence yet).
                val enrollmentPrefs = applicationContext.getSharedPreferences(
                    "ride_driver_enrollment", Context.MODE_PRIVATE
                )
                enrollmentPrefs.edit()
                    .putString("vehicle_id", result.vehicleId)
                    .putString("status", result.status)
                    .putString("document_review_status", result.documentReviewStatus)
                    .putString("pilot_access_expires_at", result.expiresAt)
                    .putString("evidence_manifest_sha256", enrollment.evidenceManifestSha256)
                    .putString("evidence_upload_status", "PENDING")
                    .apply()

                runCatching {
                    PlatformTrustCenterGateway.submit(
                        ServiceVerificationSubmission(
                            serviceType = "RIDE_DRIVER",
                            profileReference = result.vehicleId,
                            displayName = enrollment.driverDisplayName,
                            phone = enrollment.phone,
                            businessName = enrollment.vehicleDisplayName,
                            locationLabel = listOfNotNull(
                                enrollment.vehicleMake,
                                enrollment.vehicleModel,
                                enrollment.vehicleYear?.toString(),
                                enrollment.vehicleColor,
                            ).joinToString(" ").takeIf { it.isNotBlank() },
                            licenseReference = enrollment.vehiclePlate,
                            evidenceManifestSha256 = enrollment.evidenceManifestSha256,
                            evidenceFiles = enrollment.evidenceFiles,
                        ),
                    )
                }.fold(
                    onSuccess = {
                        enrollmentPrefs.edit()
                            .putString("evidence_upload_status", "UPLOADED")
                            .apply()
                        Result.success(
                            workDataOf(
                                "vehicle_id" to result.vehicleId,
                                "status" to result.status,
                                "document_review_status" to result.documentReviewStatus,
                                "pilot_access_expires_at" to result.expiresAt,
                            ),
                        )
                    },
                    onFailure = { error ->
                        Log.w(
                            "RideDriverEnrollment",
                            "Evidence upload failed (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS)",
                            error,
                        )
                        enrollmentPrefs.edit()
                            .putString("evidence_upload_status", "FAILED: ${error.message?.take(200)}")
                            .apply()
                        if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                        else {
                            enrollmentPrefs.edit()
                                .putString("evidence_upload_status", "FAILED_PERMANENT")
                                .apply()
                            Result.success(
                                workDataOf(
                                    "vehicle_id" to result.vehicleId,
                                    "status" to result.status,
                                    "document_review_status" to result.documentReviewStatus,
                                    "pilot_access_expires_at" to result.expiresAt,
                                    "evidence_upload_failed" to true,
                                ),
                            )
                        }
                    },
                )
            }
            is RideDriverEnrollmentResult.Rejected -> {
                if (result.retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            "error_code" to result.code,
                            "error_message" to result.message.take(300),
                        ),
                    )
                }
            }
            is RideDriverEnrollmentResult.TransportFailure -> {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            "error_code" to "REMOTE_TRANSPORT_FAILURE",
                            "error_message" to result.message.take(300),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ride_driver_pilot_enrollment"
        private const val MAX_ATTEMPTS = 8

        fun enqueue(
            context: Context,
            enrollment: RideDriverPilotEnrollment,
        ) {
            val request = OneTimeWorkRequestBuilder<RideDriverEnrollmentWorker>()
                .setInputData(enrollment.toData())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

private fun RideDriverPilotEnrollment.toData(): Data = workDataOf(
    "driver_display_name" to driverDisplayName,
    "country_code" to countryCode,
    "currency" to currency,
    "vehicle_reference" to vehicleReference,
    "vehicle_display_name" to vehicleDisplayName,
    "seats" to seats,
    "evidence_manifest_sha256" to evidenceManifestSha256,
    "evidence_kinds" to evidenceFiles.map(TrustEvidenceFile::kind).toTypedArray(),
    "evidence_paths" to evidenceFiles.map(TrustEvidenceFile::localPath).toTypedArray(),
    "phone" to phone,
    "email" to email,
    "vehicle_make" to vehicleMake,
    "vehicle_model" to vehicleModel,
    "vehicle_year" to (vehicleYear ?: 0),
    "vehicle_color" to vehicleColor,
    "vehicle_plate" to vehiclePlate,
)

private fun Data.toEnrollment(): RideDriverPilotEnrollment? {
    val name = getString("driver_display_name")?.takeIf { it.isNotBlank() }
        ?: return null
    val country = getString("country_code")?.takeIf { it.length == 2 }
        ?: return null
    val currency = getString("currency")?.takeIf { it.length == 3 }
        ?: return null
    val vehicleReference =
        getString("vehicle_reference")?.takeIf { it.length >= 16 } ?: return null
    val vehicleDisplay =
        getString("vehicle_display_name")?.takeIf { it.isNotBlank() } ?: return null
    val seats = getInt("seats", 0).takeIf { it in 1..16 } ?: return null
    val evidenceHash = getString("evidence_manifest_sha256")
        ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
        ?: return null
    val evidenceKinds = getStringArray("evidence_kinds").orEmpty()
    val evidencePaths = getStringArray("evidence_paths").orEmpty()
    if (evidenceKinds.size != evidencePaths.size) return null
    return RideDriverPilotEnrollment(
        driverDisplayName = name,
        countryCode = country,
        currency = currency,
        vehicleReference = vehicleReference,
        vehicleDisplayName = vehicleDisplay,
        seats = seats,
        evidenceManifestSha256 = evidenceHash,
        evidenceFiles = evidenceKinds.zip(evidencePaths) { kind, path ->
            TrustEvidenceFile(kind, path)
        },
        phone = getString("phone")?.takeIf { it.isNotBlank() },
        email = getString("email")?.takeIf { it.isNotBlank() },
        vehicleMake = getString("vehicle_make")?.takeIf { it.isNotBlank() },
        vehicleModel = getString("vehicle_model")?.takeIf { it.isNotBlank() },
        vehicleYear = getInt("vehicle_year", 0).takeIf { it > 0 },
        vehicleColor = getString("vehicle_color")?.takeIf { it.isNotBlank() },
        vehiclePlate = getString("vehicle_plate")?.takeIf { it.isNotBlank() },
    )
}
