package com.elysium369.meet.identity

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface UsageProfileSyncResult {
    data class Activated(
        val role: String,
        val mobilityRole: String?,
        val verificationRequired: Boolean,
    ) : UsageProfileSyncResult

    data object AuthenticationRequired : UsageProfileSyncResult
    data class Rejected(val code: String, val message: String) : UsageProfileSyncResult
    data class Unavailable(val message: String) : UsageProfileSyncResult
}

@Serializable
private data class UsageProfileWireError(
    val code: String,
    val message: String,
)

@Serializable
private data class UsageProfileWireData(
    val role: String,
    @SerialName("mobility_role") val mobilityRole: String? = null,
    @SerialName("verification_required") val verificationRequired: Boolean,
)

@Serializable
private data class UsageProfileWireResponse(
    val ok: Boolean,
    val data: UsageProfileWireData? = null,
    val error: UsageProfileWireError? = null,
)

object UsageProfileGateway {
    suspend fun activate(profile: OnboardingUsageProfile): UsageProfileSyncResult {
        val client = SupabaseModule.client
        val userId = client.auth.currentUserOrNull()?.id
            ?: return UsageProfileSyncResult.AuthenticationRequired
        return try {
            val response = client.postgrest.rpc(
                function = "meet_activate_usage_profile_v1",
                parameters = buildJsonObject {
                    put("p_usage_profile", profile.storageId)
                    put("p_idempotency_key", "onboarding:${profile.storageId}:$userId")
                },
            ).decodeAs<UsageProfileWireResponse>()
            if (response.ok) {
                val data = requireNotNull(response.data) {
                    "Usage profile activation omitted data"
                }
                UsageProfileSyncResult.Activated(
                    role = data.role,
                    mobilityRole = data.mobilityRole,
                    verificationRequired = data.verificationRequired,
                )
            } else {
                val error = response.error
                UsageProfileSyncResult.Rejected(
                    code = error?.code ?: "ROLE_ACTIVATION_REJECTED",
                    message = error?.message ?: "No se pudo activar el perfil",
                )
            }
        } catch (error: Exception) {
            UsageProfileSyncResult.Unavailable(
                error.message?.take(240) ?: "Servicio de perfiles no disponible",
            )
        }
    }
}
