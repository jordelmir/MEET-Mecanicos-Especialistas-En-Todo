package com.elysium369.meet.communications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunicationDeviceKeyWire(
    @SerialName("device_id") val deviceId: String,
    @SerialName("principal_id") val principalId: String,
    @SerialName("identity_key") val publicKey: String,
)
@Serializable
data class CommunicationConversationWire(
    val id: String,
    val kind: String,
    val title: String,
    @SerialName("request_state") val requestState: String,
)
@Serializable
data class CommunicationParticipantWire(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("principal_id") val principalId: String,
    val role: String,
    @SerialName("membership_state") val membershipState: String,
)
@Serializable
data class CommunicationEventWire(
    @SerialName("event_id") val eventId: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("encrypted_envelope") val envelope: String,
    @SerialName("reply_to_event_id") val replyTo: String? = null,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("client_created_at") val clientCreatedAt: String,
    @SerialName("server_sequence") val serverSequence: Long? = null,
)

@Serializable
data class CommunicationContactProfileWire(
    @SerialName("principal_id") val principalId: String,
    @SerialName("elysium_id") val elysiumId: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class CommunicationOwnIdentityWire(
    @SerialName("principal_id") val principalId:String,
    @SerialName("elysium_id") val elysiumId:String,
    @SerialName("display_name") val displayName:String,
    val about:String="",
    @SerialName("identity_state") val identityState:String,
)
