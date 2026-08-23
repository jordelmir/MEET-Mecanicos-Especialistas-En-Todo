package com.elysium369.meet.communications

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
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
}
