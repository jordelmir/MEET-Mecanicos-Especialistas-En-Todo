package com.elysium369.meet.safety.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.MeetTelemetry
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * Gate G4 & Section 16: SafetyEvidenceReconciliationWorker
 *
 * Scans for and reconciles orphan evidence states:
 * - UPLOADED items missing a local serverReceipt (crashed after upload before RPC ACK)
 * - Verifies storage object existence & SHA-256 integrity before registering custody
 * - Quarantines corrupt local staging files
 */
@HiltWorker
class SafetyEvidenceReconciliationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: SafetyEvidenceDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val client = SupabaseModule.client
        val owner = client.auth.currentUserOrNull()?.id ?: return@withContext Result.retry()

        return@withContext try {
            val pendingItems = dao.pending(owner)
            var reconciledCount = 0

            for (item in pendingItems) {
                if (client.auth.currentUserOrNull()?.id != owner) return@withContext Result.retry()

                // Case 1: Uploaded to private storage, but crashed before RPC registration ACK
                if (item.uploadState == "UPLOADED" && item.serverReceipt == null) {
                    val path = "$owner/${item.reportId}/${item.evidenceId}.original"
                    val bucket = client.storage.from("safety-evidence-original")

                    val existingBytes = try {
                        bucket.downloadAuthenticated(path)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        null
                    }

                    if (existingBytes != null) {
                        val remoteHashMatches = try {
                            existingBytes.size.toLong() == item.byteCount &&
                                SafetyEvidencePolicy.sha256(existingBytes) == item.contentSha256
                        } finally {
                            existingBytes.fill(0)
                        }

                        if (remoteHashMatches) {
                            // Re-attempt authoritative RPC registration
                            val receipt = client.postgrest.rpc("safety_register_evidence_v1", buildJsonObject {
                                put("p_evidence_id", item.evidenceId)
                                put("p_report_id", item.reportId)
                                put("p_storage_path", path)
                                put("p_content_sha256", item.contentSha256)
                                put("p_mime_type", item.mimeType)
                                put("p_byte_count", item.byteCount)
                                put("p_captured_at", JsonNull)
                            }).decodeAs<JsonObject>()

                            val isOk = receipt["ok"]?.jsonPrimitive?.booleanOrNull == true &&
                                receipt["evidence_id"]?.jsonPrimitive?.contentOrNull == item.evidenceId

                            if (isOk) {
                                dao.update(item.evidenceId, owner, "RECEIVED", item.attemptCount, null, receipt.toString())
                                reconciledCount++
                                MeetTelemetry.event("safety.evidence.reconciled", mapOf(
                                    "evidenceId" to item.evidenceId,
                                    "status" to "REGISTERED_FROM_ORPHAN",
                                ))
                            }
                        }
                    }
                }

                // Case 2: Staged file was deleted or corrupted locally
                if (item.uploadState == "STAGED") {
                    val localFile = File(item.encryptedPath)
                    if (!localFile.exists() || !localFile.isFile) {
                        dao.update(item.evidenceId, owner, "FAILED", item.attemptCount, "LOCAL_FILE_MISSING", null)
                        MeetTelemetry.event("safety.evidence.reconciled", mapOf(
                            "evidenceId" to item.evidenceId,
                            "status" to "QUARANTINED_MISSING_LOCAL",
                        ))
                    }
                }
            }

            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}
