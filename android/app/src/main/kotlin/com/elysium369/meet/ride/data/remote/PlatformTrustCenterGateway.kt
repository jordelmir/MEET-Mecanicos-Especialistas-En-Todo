package com.elysium369.meet.ride.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
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
)

@Serializable
private data class TrustQueueResponse(
    val items: List<TrustVerificationApplication> = emptyList(),
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
)

data class CapabilityVerificationSubmission(
    val capability: String,
    val profileReference: String,
    val displayName: String,
    val evidenceManifestSha256: String,
)

object PlatformTrustCenterGateway {
    suspend fun hasOwnerAccess(): Boolean {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return false
        return client.postgrest.rpc("meet_is_platform_owner").decodeAs<Boolean>()
    }

    suspend fun loadQueue(status: String = "PENDING"): List<TrustVerificationApplication> =
        SupabaseModule.client.postgrest.rpc(
            function = "meet_owner_verification_queue_v1",
            parameters = buildJsonObject {
                put("p_status", status)
                put("p_limit", 100)
            },
        ).decodeAs<TrustQueueResponse>().items

    suspend fun loadOwnApplications(): List<TrustVerificationApplication> {
        val userId = SupabaseModule.client.auth.currentUserOrNull()?.id
            ?: return emptyList()
        return SupabaseModule.client.postgrest["service_verification_applications"]
            .select {
                filter { eq("applicant_user_id", userId) }
            }
            .decodeList()
    }

    suspend fun decide(applicationId: String, decision: String, reason: String) {
        SupabaseModule.client.postgrest.rpc(
            function = "meet_owner_decide_verification_v2",
            parameters = buildJsonObject {
                put("p_application_id", applicationId)
                put("p_decision", decision)
                put("p_reason", reason)
            },
        )
    }

    suspend fun submit(application: ServiceVerificationSubmission) {
        val client = SupabaseModule.client
        check(client.auth.currentUserOrNull() != null) { "Authenticated account required" }
        val serviceType = requireNotNull(
            ServiceVerificationTypePolicy.canonicalLegacyType(application.serviceType),
        ) { "Unsupported verification service type" }
        client.postgrest.rpc(
            function = "meet_submit_service_verification_v1",
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
            },
        )
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
        return client.postgrest.rpc(
            function = "meet_submit_capability_application_v1",
            parameters = buildJsonObject {
                put("p_capability", capability)
                put("p_profile_reference", application.profileReference)
                put("p_display_name", application.displayName)
                put("p_evidence_manifest_sha256", application.evidenceManifestSha256)
            },
        ).decodeAs<String>()
    }
}
