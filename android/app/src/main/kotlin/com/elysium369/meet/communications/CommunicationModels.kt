package com.elysium369.meet.communications

enum class CommunicationProofState {
    MODEL_EXISTS,
    CLIENT_IMPLEMENTED,
    SERVER_AUTHORITATIVE,
    PHYSICALLY_VERIFIED,
}

enum class ConversationKind { DIRECT, SERVICE, GROUP, PERSONAL }

enum class MessageRequestState { PENDING, ACCEPTED, REJECTED, BLOCKED }

data class ConversationSummary(
    val id: String,
    val title: String,
    val kind: ConversationKind,
    val serviceVertical: String?,
    val serviceReferenceId: String?,
    val requestState: MessageRequestState,
    val participantCount: Int? = null,
    val lastActivityAtEpochMs: Long?,
    val proofState: CommunicationProofState,
)

data class DecryptedMessage(
    val id: String,
    val senderPrincipalId: String,
    val body: String,
    val isMine: Boolean,
    val createdAtEpochMs: Long,
    val deliveryState: String,
    val eventType: String = "TEXT",
    val replyToEventId: String? = null,
    val localMediaPath: String? = null,
    val decryptionFailed: Boolean = false,
)

sealed interface SendMessageOutcome {
    data class SentLocally(val eventId: String) : SendMessageOutcome
    data object WaitingForAuthorizedParticipant : SendMessageOutcome
    data object EmptyMessage : SendMessageOutcome
    data object ConversationUnavailable : SendMessageOutcome
}

sealed interface StartCallOutcome {
    data object WaitingForAuthorizedParticipant : StartCallOutcome
    data object ServerTransportNotConfigured : StartCallOutcome
    data object AuthenticationRequired : StartCallOutcome
    data object InsecureEndpointRejected : StartCallOutcome
    data class Failed(val safeCode: String) : StartCallOutcome
    data class Ready(val callId: String) : StartCallOutcome
}
