package com.elysium369.meet.communications

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class AuthoritativeConversation(
    val id: String,
    val participantIds: List<String>,
)

@Serializable
private data class ParticipantWire(
    @SerialName("principal_id") val principalId: String,
)

@Serializable
private data class DiscoveryWire(
    @SerialName("principal_id") val principalId: String,
    @SerialName("elysium_id") val elysiumId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("matched_medium") val matchedMedium: String,
    @SerialName("alias_proof_state") val aliasProofState: String,
)

sealed interface RemoteDiscoveryOutcome {
    data class Found(val contacts: List<RemoteDiscoveredContact>) : RemoteDiscoveryOutcome
    data object AuthenticationRequired : RemoteDiscoveryOutcome
    data object RateLimited : RemoteDiscoveryOutcome
    data object Unavailable : RemoteDiscoveryOutcome
}

data class RemoteDiscoveredContact(
    val principalId: String,
    val elysiumId: String,
    val displayName: String,
    val matchedMedium: String,
    val aliasProofState: String,
)

@Singleton
class CommunicationRemoteGateway @Inject constructor() {
    suspend fun ensureServiceConversation(vertical: String, referenceId: String): AuthoritativeConversation? {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return null
        val function = when (vertical) {
            "ride" -> "ensure_ride_communication"
            "universal" -> "ensure_universal_service_communication"
            else -> return null
        }
        return runCatching {
            val parameter = if (vertical == "ride") "p_ride_request_id" else "p_request_id"
            val conversationId = client.postgrest.rpc(
                function,
                buildJsonObject { put(parameter, referenceId) },
            ).decodeAs<String>()
            val participants = client.postgrest["communication_participants"]
                .select { filter { eq("conversation_id", conversationId) } }
                .decodeList<ParticipantWire>()
                .map(ParticipantWire::principalId)
            AuthoritativeConversation(conversationId, participants)
        }.getOrNull()
    }

    suspend fun lookupExact(medium: ContactDiscoveryMedium, normalizedValue: String): RemoteDiscoveryOutcome {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return RemoteDiscoveryOutcome.AuthenticationRequired
        return runCatching {
            val rows = client.postgrest.rpc(
                "communication_lookup_identity_exact",
                buildJsonObject {
                    put("p_medium", medium.name)
                    put("p_value", normalizedValue)
                },
            ).decodeList<DiscoveryWire>()
            RemoteDiscoveryOutcome.Found(
                rows.map { row ->
                    RemoteDiscoveredContact(
                        principalId = row.principalId,
                        elysiumId = row.elysiumId,
                        displayName = row.displayName,
                        matchedMedium = row.matchedMedium,
                        aliasProofState = row.aliasProofState,
                    )
                },
            )
        }.getOrElse { error ->
            if (error.message.orEmpty().contains("DISCOVERY_RATE_LIMITED")) {
                RemoteDiscoveryOutcome.RateLimited
            } else {
                RemoteDiscoveryOutcome.Unavailable
            }
        }
    }

    suspend fun ensureIdentity(
        elysiumId: String,
        displayName: String,
        about: String,
        phone: String?,
        phoneDiscovery: String,
    ): Boolean = authenticatedRpc(
        "communication_ensure_identity",
        buildJsonObject {
            put("p_elysium_id", elysiumId.removePrefix("@"))
            put("p_display_name", displayName)
            put("p_about", about)
            if (phone == null) put("p_phone", kotlinx.serialization.json.JsonNull) else put("p_phone", phone)
            put("p_phone_discovery", phoneDiscovery)
        },
    )

    suspend fun createDirectRequest(targetPrincipalId: String): String? {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return null
        return runCatching {
            client.postgrest.rpc(
                "communication_create_direct_request",
                buildJsonObject { put("p_target_id", targetPrincipalId) },
            ).decodeAs<String>()
        }.getOrNull()
    }

    suspend fun respondToMessageRequest(conversationId: String, accept: Boolean): Boolean = authenticatedRpc(
        "communication_respond_message_request",
        buildJsonObject {
            put("p_conversation_id", conversationId)
            put("p_accept", accept)
        },
    )

    suspend fun savePrivacy(settings: CommunicationPrivacySettings): Boolean = authenticatedRpc(
        "communication_set_privacy",
        buildJsonObject {
            put("p_settings", buildJsonObject {
                put("findByElysiumId", settings.findByElysiumId)
                put("findByEmail", settings.findByEmail)
                put("findByPhone", settings.findByPhone)
                put("profilePhotoVisibility", settings.profilePhotoVisibility)
                put("profileVisibility", settings.profileVisibility)
                put("lastActiveVisibility", settings.lastActiveVisibility)
                put("onlineVisibility", settings.onlineVisibility)
                put("readReceiptsEnabled", settings.readReceiptsEnabled)
                put("typingIndicatorsEnabled", settings.typingIndicatorsEnabled)
                put("callPermission", settings.callPermission)
                put("groupInvitePermission", settings.groupInvitePermission)
                put("meshDiscoverability", settings.meshDiscoverability)
                put("relayParticipation", settings.relayParticipation)
                put("relayOnlyWhileCharging", settings.relayOnlyWhileCharging)
                put("relayMinimumBatteryPercent", settings.relayMinimumBatteryPercent)
            })
        },
    )

    suspend fun blockPrincipal(targetPrincipalId: String): Boolean = authenticatedRpc(
        "communication_block_principal",
        buildJsonObject { put("p_target_id", targetPrincipalId) },
    )

    suspend fun unblockPrincipal(targetPrincipalId: String): Boolean {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return false
        return runCatching {
            client.postgrest["communication_blocks"].delete {
                filter { eq("blocked_id", targetPrincipalId) }
            }
            true
        }.getOrDefault(false)
    }

    suspend fun heartbeat(reachability: String, deviceId: String?): Boolean = authenticatedRpc(
        "communication_presence_heartbeat",
        buildJsonObject {
            put("p_reachability", reachability)
            if (deviceId == null) put("p_device_id", kotlinx.serialization.json.JsonNull) else put("p_device_id", deviceId)
        },
    )

    private suspend fun authenticatedRpc(function: String, parameters: JsonObject): Boolean {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) return false
        return runCatching {
            client.postgrest.rpc(function, parameters)
            true
        }.getOrDefault(false)
    }
}
