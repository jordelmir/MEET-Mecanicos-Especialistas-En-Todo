package com.elysium369.meet.safety.work

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
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.MeetTelemetry
import com.elysium369.meet.safety.crypto.AeadBlob
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.data.SafetyRuntimeFeatureGates
import com.elysium369.meet.safety.data.local.SafetyReportDao
import com.elysium369.meet.safety.evidence.SafetyEvidenceDao
import com.elysium369.meet.safety.evidence.SafetyEvidencePolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

/** Replays immutable local intent; custody becomes received only after matching server receipt. */
@HiltWorker
class SafetyEvidenceUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val dao: SafetyEvidenceDao,
    private val reports: SafetyReportDao,
    private val cipher: SafetyPayloadCipher,
    private val gates: SafetyRuntimeFeatureGates,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val client = SupabaseModule.client
        val owner = client.auth.currentUserOrNull()?.id ?: return@withContext Result.retry()
        val pending = dao.pending(owner)
        var retry = pending.size == 20
        for (item in pending) {
            if (client.auth.currentUserOrNull()?.id != owner) return@withContext Result.retry()
            val report = reports.get(item.reportId) ?: continue // A staged draft is never sent.
            if (report.ownerUserId != owner) continue
            if (report.serverVersion <= 0 || report.syncState != "SYNCED") {
                retry = true
                continue
            }
            try { gates.requireEnabled("safety_evidence_upload") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withContext Result.retry() }
            var bytes: ByteArray? = null
            try {
                if (client.auth.currentUserOrNull()?.id != owner) return@withContext Result.retry()
                val file = File(item.encryptedPath)
                check(file.canonicalPath.startsWith(File(applicationContext.noBackupFilesDir, "safety_evidence/$owner").canonicalPath + File.separator)) { "INVALID_LOCAL_PATH" }
                check(file.isFile && file.length() <= SafetyEvidencePolicy.MAX_BYTES + 128) { "LOCAL_EVIDENCE_UNAVAILABLE" }
                val plaintext = cipher.decryptAead(AeadBlob.fromWire(file.readBytes()), SafetyEvidencePolicy.associatedData(owner, item.reportId, item.evidenceId))
                bytes = plaintext
                check(plaintext.size.toLong() == item.byteCount && SafetyEvidencePolicy.sha256(plaintext) == item.contentSha256) { "EVIDENCE_INTEGRITY_MISMATCH" }
                dao.update(item.evidenceId, owner, "UPLOADING", item.attemptCount + 1, null)
                val path = "$owner/${item.reportId}/${item.evidenceId}.original"
                val bucket = client.storage.from("safety-evidence-original")
                try {
                    bucket.upload(path, plaintext, upsert = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (uploadError: Exception) {
                    // Lost upload response / duplicate replay: accept only byte-identical private object.
                    if (client.auth.currentUserOrNull()?.id != owner) throw uploadError
                    val existing = try { bucket.downloadAuthenticated(path) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { throw uploadError }
                    try {
                        check(existing.size.toLong() == item.byteCount && SafetyEvidencePolicy.sha256(existing) == item.contentSha256) { "REMOTE_EVIDENCE_INTEGRITY_MISMATCH" }
                    } finally { existing.fill(0) }
                }
                if (client.auth.currentUserOrNull()?.id != owner) return@withContext Result.retry()
                dao.update(item.evidenceId, owner, "UPLOADED", item.attemptCount + 1, null)
                val receipt = client.postgrest.rpc("safety_register_evidence_v1", buildJsonObject {
                    put("p_evidence_id", item.evidenceId)
                    put("p_report_id", item.reportId)
                    put("p_storage_path", path)
                    put("p_content_sha256", item.contentSha256)
                    put("p_mime_type", item.mimeType)
                    put("p_byte_count", item.byteCount)
                    put("p_captured_at", JsonNull) // Selection/staging time is not capture time.
                }).decodeAs<JsonObject>()
                check(receipt["ok"]?.jsonPrimitive?.booleanOrNull == true && receipt["evidence_id"]?.jsonPrimitive?.contentOrNull == item.evidenceId && receipt["storage_path"]?.jsonPrimitive?.contentOrNull == path && receipt["content_sha256"]?.jsonPrimitive?.contentOrNull == item.contentSha256) { "INVALID_EVIDENCE_RECEIPT" }
                dao.update(item.evidenceId, owner, "RECEIVED", item.attemptCount + 1, null, receipt.toString())
                // Privacy-safe: no evidence URLs, content, GPS, or report narrative.
                MeetTelemetry.event("safety.evidence.registered", mapOf(
                    "mimeType" to item.mimeType,
                    "attemptCount" to (item.attemptCount + 1),
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val integrityFailure = error.message in setOf("EVIDENCE_INTEGRITY_MISMATCH", "REMOTE_EVIDENCE_INTEGRITY_MISMATCH", "INVALID_LOCAL_PATH", "LOCAL_EVIDENCE_UNAVAILABLE", "INVALID_EVIDENCE_RECEIPT") || error is javax.crypto.AEADBadTagException
                val failed = integrityFailure || item.attemptCount >= 11
                dao.update(item.evidenceId, owner, if (failed) "FAILED" else "RETRY", item.attemptCount + 1, if (integrityFailure) "INTEGRITY_CHECK_FAILED" else "UPLOAD_OR_REGISTRATION_PENDING")
                if (failed) {
                    MeetTelemetry.event("safety.evidence.failed", mapOf(
                        "failureCode" to (if (integrityFailure) "INTEGRITY_CHECK_FAILED" else "MAX_RETRIES"),
                        "attemptCount" to (item.attemptCount + 1),
                    ))
                }
                if (!failed) retry = true
            } finally { bytes?.fill(0) }
        }
        if (retry) Result.retry() else Result.success()
    }
}

object SafetyEvidenceScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SafetyEvidenceUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("safety-evidence-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
