package com.elysium369.meet.ride.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.TrustCenterObservability
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

@Serializable
data class TrustVerificationApplication(
    val id: String,
    @SerialName("applicant_user_id") val applicantUserId: String,
    @SerialName("applicant_email") val applicantEmail: String? = null,
    @SerialName("service_type") val serviceType: String,
    @SerialName("profile_reference") val profileReference: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("business_name") val businessName: String? = null,
    val phone: String? = null,
    @SerialName("location_label") val locationLabel: String? = null,
    @SerialName("license_reference") val licenseReference: String? = null,
    @SerialName("evidence_manifest_sha256") val evidenceManifestSha256: String? = null,
    val status: String,
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
    val evidence: List<TrustEvidenceItem> = emptyList(),
)

@Serializable
data class TrustEvidenceItem(
    val kind: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("content_sha256") val contentSha256: String,
    @SerialName("byte_count") val byteCount: Long,
    @SerialName("mime_type") val mimeType: String,
)

data class TrustEvidenceFile(val kind: String, val localPath: String)

@Serializable
data class TrustQueueCounts(
    @SerialName("PENDING") val pending: Int = 0,
    @SerialName("APPROVED") val approved: Int = 0,
    @SerialName("REJECTED") val rejected: Int = 0,
    @SerialName("SUSPENDED") val suspended: Int = 0,
    @SerialName("ALL") val all: Int = 0,
)

@Serializable
data class TrustQueueSnapshot(
    val items: List<TrustVerificationApplication> = emptyList(),
    val counts: TrustQueueCounts = TrustQueueCounts(),
    val status: String? = null,
    @SerialName("server_timestamp") val serverTimestamp: String? = null,
)

@Serializable
data class TrustSubmissionReceipt(
    val id: String,
    val status: String,
    @SerialName("service_type") val serviceType: String,
    @SerialName("correlation_id") val correlationId: String,
    @SerialName("submitted_at") val submittedAt: String,
)

data class ServiceVerificationSubmission(
    val serviceType: String,
    val profileReference: String,
    val displayName: String,
    val businessName: String? = null,
    val phone: String? = null,
    val locationLabel: String? = null,
    val licenseReference: String? = null,
    val evidenceManifestSha256: String? = null,
    val evidenceFiles: List<TrustEvidenceFile> = emptyList(),
)

data class CapabilityVerificationSubmission(
    val capability: String,
    val profileReference: String,
    val displayName: String,
    val evidenceManifestSha256: String,
)

enum class TrustRealtimeSignal { SUBSCRIBED, CHANGE }

object PlatformTrustCenterGateway {
    private const val TRUST_EVIDENCE_BUCKET = "trust-verification-evidence"
    private const val MAX_EVIDENCE_BYTES = 12L * 1024L * 1024L

    suspend fun hasOwnerAccess(): Boolean {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return false
        return client.postgrest.rpc("meet_is_platform_owner").decodeAs<Boolean>()
    }

    suspend fun loadQueue(status: String = "PENDING"): TrustQueueSnapshot {
        val trace = TrustCenterObservability.start("LOAD_QUEUE")
        return try {
            SupabaseModule.client.postgrest.rpc(
                function = "meet_owner_verification_queue_v3",
                parameters = buildJsonObject {
                    put("p_status", status)
                    put("p_limit", 100)
                },
            ).decodeAs<TrustQueueSnapshot>().also { snapshot ->
                TrustCenterObservability.succeeded(
                    trace,
                    resultCode = "LOADED",
                    queueStatus = status,
                    itemCount = snapshot.items.size,
                )
            }
        } catch (error: Exception) {
            TrustCenterObservability.failed(trace, error)
            throw error
        }
    }

    suspend fun loadOwnApplications(): List<TrustVerificationApplication> {
        if (SupabaseModule.client.auth.currentUserOrNull() == null) return emptyList()
        val trace = TrustCenterObservability.start("LOAD_OWN_APPLICATIONS")
        return try {
            SupabaseModule.client.postgrest.rpc(
                function = "meet_own_verification_applications_v1",
            ).decodeAs<TrustQueueSnapshot>().items.also { items ->
                TrustCenterObservability.succeeded(trace, "LOADED", itemCount = items.size)
            }
        } catch (error: Exception) {
            TrustCenterObservability.failed(trace, error)
            throw error
        }
    }

    suspend fun decide(applicationId: String, decision: String, reason: String) {
        val trace = TrustCenterObservability.start("DECIDE_APPLICATION")
        try {
            SupabaseModule.client.postgrest.rpc(
                function = "meet_owner_decide_verification_v3",
                parameters = buildJsonObject {
                    put("p_application_id", applicationId)
                    put("p_decision", decision)
                    put("p_reason", reason)
                },
            )
            TrustCenterObservability.succeeded(trace, decision)
        } catch (error: Exception) {
            TrustCenterObservability.failed(trace, error)
            throw error
        }
    }

    suspend fun submit(application: ServiceVerificationSubmission): TrustSubmissionReceipt {
        val client = SupabaseModule.client
        check(client.auth.currentUserOrNull() != null) { "Authenticated account required" }
        val serviceType = requireNotNull(
            ServiceVerificationTypePolicy.canonicalSubmissionType(application.serviceType),
        ) { "Unsupported verification service type" }
        val trace = TrustCenterObservability.start("SUBMIT_APPLICATION", serviceType)
        return try {
            val evidence = uploadEvidence(application, serviceType)
            client.postgrest.rpc(
                function = if (evidence.isEmpty()) {
                    "meet_submit_service_verification_v2"
                } else {
                    "meet_submit_service_verification_v3"
                },
                parameters = buildJsonObject {
                    put("p_service_type", serviceType)
                    put("p_profile_reference", application.profileReference)
                    put("p_display_name", application.displayName)
                    application.businessName?.let { put("p_business_name", it) }
                    application.phone?.let { put("p_phone", it) }
                    application.locationLabel?.let { put("p_location_label", it) }
                    application.licenseReference?.let { put("p_license_reference", it) }
                    application.evidenceManifestSha256?.let {
                        put("p_evidence_manifest_sha256", it)
                    }
                    put("p_correlation_id", trace.correlationId)
                    if (evidence.isNotEmpty()) {
                        put("p_evidence", buildJsonArray {
                            evidence.forEach { item ->
                                add(buildJsonObject {
                                    put("kind", item.kind)
                                    put("storage_path", item.storagePath)
                                    put("content_sha256", item.contentSha256)
                                    put("byte_count", item.byteCount)
                                    put("mime_type", item.mimeType)
                                })
                            }
                        })
                    }
                },
            ).decodeAs<TrustSubmissionReceipt>().also { receipt ->
                TrustCenterObservability.succeeded(trace, receipt.status)
            }
        } catch (error: Exception) {
            TrustCenterObservability.failed(trace, error)
            throw error
        }
    }

    suspend fun downloadEvidence(storagePath: String): ByteArray {
        check(hasOwnerAccess()) { "Platform owner required" }
        return SupabaseModule.client.storage
            .from(TRUST_EVIDENCE_BUCKET)
            .downloadAuthenticated(storagePath)
    }

    /**
     * Fallback: when the evidence table has no rows for an application,
     * list files directly from Supabase Storage by applicant's userId prefix.
     * This handles cases where the enrollment worker's submit() used the v2 RPC
     * (no evidence table insert) or the evidence upload partially failed.
     */
    suspend fun listEvidenceFromStorage(
        applicantUserId: String,
        serviceType: String,
    ): List<TrustEvidenceItem> {
        if (!hasOwnerAccess()) return emptyList()
        return try {
            val bucket = SupabaseModule.client.storage.from(TRUST_EVIDENCE_BUCKET)
            val prefix = "$applicantUserId/${serviceType.lowercase()}/"
            val objects = bucket.list(prefix)
            objects.mapNotNull { obj ->
                val name = obj.name
                val parts = name.split("/")
                val kind = parts.getOrNull(2)?.substringBeforeLast("-") ?: return@mapNotNull null
                val sha = name.substringAfterLast("-")
                if (!kind.matches(Regex("[a-z0-9_]{2,40}")) || !sha.matches(Regex("[a-f0-9]{64}"))) {
                    return@mapNotNull null
                }
                TrustEvidenceItem(
                    kind = kind,
                    storagePath = name,
                    contentSha256 = sha,
                    byteCount = obj.metadata?.get("size")?.toString()?.toLongOrNull() ?: 0L,
                    mimeType = obj.metadata?.get("mimetype")?.toString() ?: "image/jpeg",
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("PlatformTrustCenter", "Storage fallback list failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun uploadEvidence(
        application: ServiceVerificationSubmission,
        serviceType: String,
    ): List<TrustEvidenceItem> {
        if (application.evidenceFiles.isEmpty()) return emptyList()
        val userId = requireNotNull(SupabaseModule.client.auth.currentUserOrNull()?.id)
        val profile = application.profileReference
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .take(80)
        val bucket = SupabaseModule.client.storage.from(TRUST_EVIDENCE_BUCKET)
        val uploaded = mutableListOf<TrustEvidenceItem>()
        for (evidence in application.evidenceFiles.distinctBy { it.kind }) {
            if (!evidence.kind.matches(Regex("[a-z0-9_]{2,40}"))) continue
            val file = File(evidence.localPath)
            if (!file.isFile || file.length() !in 1..MAX_EVIDENCE_BYTES) {
                android.util.Log.w(
                    "PlatformTrustCenter",
                    "Skipping evidence ${evidence.kind}: file missing or invalid at ${evidence.localPath}",
                )
                continue
            }
            runCatching {
                val bytes = file.readBytes()
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                val mime = when (file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val remotePath = "$userId/${serviceType.lowercase()}/$profile/${evidence.kind}-$hash"
                bucket.upload(remotePath, bytes, upsert = true)
                TrustEvidenceItem(
                    kind = evidence.kind,
                    storagePath = remotePath,
                    contentSha256 = hash,
                    byteCount = bytes.size.toLong(),
                    mimeType = mime,
                )
            }.onSuccess { uploaded.add(it) }
                .onFailure { e ->
                    android.util.Log.w(
                        "PlatformTrustCenter",
                        "Failed to upload evidence ${evidence.kind}: ${e.message}",
                    )
                }
        }
        return uploaded
    }

    suspend fun submitCapability(application: CapabilityVerificationSubmission): String {
        val client = SupabaseModule.client
        check(client.auth.currentUserOrNull() != null) { "Authenticated account required" }
        val capability = requireNotNull(
            ServiceVerificationTypePolicy.canonicalCapability(application.capability),
        ) { "Unsupported capability type" }
        require(application.evidenceManifestSha256.matches(Regex("[a-f0-9]{64}"))) {
            "Evidence manifest SHA-256 required"
        }
        return submit(
            ServiceVerificationSubmission(
                serviceType = capability,
                profileReference = application.profileReference,
                displayName = application.displayName,
                evidenceManifestSha256 = application.evidenceManifestSha256,
            ),
        ).id
    }

    fun realtimeWakeUps(): Flow<TrustRealtimeSignal> = flow {
        val channel = SupabaseModule.client.channel("elysium-platform-trust-center")
        val changes = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "service_verification_applications"
            }
            .map { TrustRealtimeSignal.CHANGE }
        try {
            channel.subscribe()
            TrustCenterObservability.realtime("SUBSCRIBED")
            emit(TrustRealtimeSignal.SUBSCRIBED)
            emitAll(changes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TrustCenterObservability.realtime("INTERRUPTED")
            throw error
        } finally {
            channel.unsubscribe()
            TrustCenterObservability.realtime("DISCONNECTED")
        }
    }

    /**
     * Realtime subscription for the current user's own verification applications.
     * Emits [TrustRealtimeSignal.CHANGE] whenever the owner approves/rejects/suspends
     * an application belonging to the authenticated user, enabling instant UI updates.
     */
    fun ownVerificationChanges(): Flow<TrustRealtimeSignal> = flow {
        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id ?: return@flow
        val channel = SupabaseModule.client.channel("elysium-own-verification-$userId")
        val changes = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "service_verification_applications"
                filter = "applicant_user_id=eq.$userId"
            }
            .map { TrustRealtimeSignal.CHANGE }
        try {
            channel.subscribe()
            TrustCenterObservability.realtime("OWN_VERIFICATION_SUBSCRIBED")
            emit(TrustRealtimeSignal.SUBSCRIBED)
            emitAll(changes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            TrustCenterObservability.realtime("OWN_VERIFICATION_INTERRUPTED")
            throw error
        } finally {
            channel.unsubscribe()
            TrustCenterObservability.realtime("OWN_VERIFICATION_DISCONNECTED")
        }
    }
}
