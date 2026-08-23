package com.elysium369.meet.communications

import com.elysium369.meet.data.local.dao.CommunicationDao
import com.elysium369.meet.data.local.entities.CommunicationCallEntity
import com.elysium369.meet.data.local.entities.CommunicationConversationEntity
import com.elysium369.meet.data.local.entities.CommunicationEventEntity
import com.elysium369.meet.data.local.entities.CommunicationParticipantEntity
import com.elysium369.meet.identity.ActivePrincipalKernel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ElysiumCommunicationRepository @Inject constructor(
    private val dao: CommunicationDao,
    private val principalKernel: ActivePrincipalKernel,
    private val cipher: DeviceMessageCipher,
    private val callTransport: ElysiumCallTransport,
    private val remoteGateway: CommunicationRemoteGateway,
) {
    val callState = callTransport.state
    val conversations: Flow<List<ConversationSummary>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeConversations(principal.id).map { rows -> rows.map(::toSummary) }
        }

    suspend fun ensureServiceConversation(
        serviceVertical: String,
        serviceReferenceId: String,
        title: String,
        authorizedPeerPrincipalId: String? = null,
        authorizedPeerName: String? = null,
    ): String {
        require(serviceVertical.isNotBlank()) { "Service vertical is required" }
        require(serviceReferenceId.isNotBlank()) { "Service reference is required" }
        val principal = principalKernel.current()
        val authoritative = remoteGateway.ensureServiceConversation(serviceVertical, serviceReferenceId)
        dao.findServiceConversation(principal.id, serviceVertical, serviceReferenceId)?.let {
            return it.conversationId
        }

        val now = System.currentTimeMillis()
        val conversationId = authoritative?.id
            ?: stableConversationId(principal.id, serviceVertical, serviceReferenceId)
        runCatching {
            dao.insertConversation(
                CommunicationConversationEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    kind = ConversationKind.SERVICE.name,
                    title = title.trim().take(160),
                    serviceVertical = serviceVertical,
                    serviceReferenceId = serviceReferenceId,
                    proofState = if (authoritative != null) {
                        CommunicationProofState.SERVER_AUTHORITATIVE.name
                    } else {
                        CommunicationProofState.CLIENT_IMPLEMENTED.name
                    },
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        dao.upsertParticipant(
            CommunicationParticipantEntity(
                conversationId = conversationId,
                ownerPrincipalId = principal.id,
                participantPrincipalId = principal.id,
                displayName = "Tú",
                role = "CUSTOMER",
                joinedAtEpochMs = now,
            ),
        )
        if (!authorizedPeerPrincipalId.isNullOrBlank()) {
            dao.upsertParticipant(
                CommunicationParticipantEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    participantPrincipalId = authorizedPeerPrincipalId,
                    displayName = authorizedPeerName?.trim().takeUnless { it.isNullOrBlank() } ?: "Proveedor autorizado",
                    role = "SERVICE_PROVIDER",
                    joinedAtEpochMs = now,
                ),
            )
        }
        authoritative?.participantIds
            ?.filter { it != principal.id }
            ?.forEach { participantId ->
                dao.upsertParticipant(
                    CommunicationParticipantEntity(
                        conversationId = conversationId,
                        ownerPrincipalId = principal.id,
                        participantPrincipalId = participantId,
                        displayName = "Participante autorizado",
                        role = "SERVICE_PROVIDER",
                        joinedAtEpochMs = now,
                    ),
                )
            }
        return conversationId
    }

    fun observeConversation(conversationId: String): Flow<ConversationSummary?> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            combine(
                dao.observeConversation(conversationId, principal.id),
                dao.observeParticipants(conversationId, principal.id),
            ) { conversation, participants ->
                conversation?.let { toSummary(it).copy(participantCount = participants.count { row -> row.membershipState == "ACTIVE" }) }
            }
        }

    fun observeMessages(conversationId: String): Flow<List<DecryptedMessage>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeEvents(conversationId, principal.id).map { events ->
                events.map { event ->
                    val body = runCatching {
                        cipher.decrypt(
                            LocalCipherPayload(event.localCiphertextBase64, event.localNonceBase64),
                            associatedData(event.conversationId, event.eventId, event.senderPrincipalId),
                        )
                    }
                    DecryptedMessage(
                        id = event.eventId,
                        senderPrincipalId = event.senderPrincipalId,
                        body = body.getOrElse { "Mensaje cifrado no disponible en este dispositivo" },
                        isMine = event.senderPrincipalId == principal.id,
                        createdAtEpochMs = event.createdAtEpochMs,
                        deliveryState = event.syncState,
                        decryptionFailed = body.isFailure,
                    )
                }
            }
        }

    suspend fun sendText(conversationId: String, text: String): SendMessageOutcome {
        val normalized = text.trim()
        if (normalized.isEmpty()) return SendMessageOutcome.EmptyMessage
        val principal = principalKernel.current()
        if (dao.activeParticipantCount(conversationId, principal.id) < 2) {
            return SendMessageOutcome.WaitingForAuthorizedParticipant
        }

        val eventId = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(
            normalized.take(4000),
            associatedData(conversationId, eventId, principal.id),
        )
        val inserted = dao.appendEvent(
            CommunicationEventEntity(
                eventId = eventId,
                conversationId = conversationId,
                ownerPrincipalId = principal.id,
                senderPrincipalId = principal.id,
                senderDeviceId = principalKernel.localDeviceId,
                eventType = "TEXT",
                localCiphertextBase64 = encrypted.ciphertextBase64,
                localNonceBase64 = encrypted.nonceBase64,
                syncState = "LOCAL_ONLY",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return if (inserted) SendMessageOutcome.SentLocally(eventId) else SendMessageOutcome.ConversationUnavailable
    }

    suspend fun startAudioCall(conversationId: String): StartCallOutcome {
        val principal = principalKernel.current()
        if (dao.activeParticipantCount(conversationId, principal.id) < 2) {
            return StartCallOutcome.WaitingForAuthorizedParticipant
        }

        val callId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val base = CommunicationCallEntity(
            callId = callId,
            conversationId = conversationId,
            ownerPrincipalId = principal.id,
            direction = "OUTGOING",
            mediaType = "AUDIO",
            state = "CONNECTING",
            transportProofState = CommunicationProofState.CLIENT_IMPLEMENTED.name,
            startedAtEpochMs = startedAt,
        )
        dao.upsertCall(base)

        return when (val outcome = callTransport.connectAudio(conversationId, principal.id)) {
            CallTransportOutcome.Connected -> {
                dao.upsertCall(
                    base.copy(
                        state = "ACTIVE",
                        transportProofState = CommunicationProofState.SERVER_AUTHORITATIVE.name,
                        answeredAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                StartCallOutcome.Ready(callId)
            }
            CallTransportOutcome.NotConfigured -> failedCall(base, "SERVER_TRANSPORT_NOT_CONFIGURED").let {
                StartCallOutcome.ServerTransportNotConfigured
            }
            CallTransportOutcome.AuthenticationRequired -> failedCall(base, "AUTHENTICATION_REQUIRED").let {
                StartCallOutcome.AuthenticationRequired
            }
            CallTransportOutcome.RejectedInsecureEndpoint -> failedCall(base, "INSECURE_ENDPOINT_REJECTED").let {
                StartCallOutcome.InsecureEndpointRejected
            }
            is CallTransportOutcome.Failed -> failedCall(base, outcome.safeCode).let {
                StartCallOutcome.Failed(outcome.safeCode)
            }
        }
    }

    suspend fun endCall() {
        callTransport.end()
    }

    private suspend fun failedCall(base: CommunicationCallEntity, code: String) {
        dao.upsertCall(
            base.copy(
                state = "FAILED",
                endedAtEpochMs = System.currentTimeMillis(),
                failureCode = code,
            ),
        )
    }

    private fun toSummary(row: CommunicationConversationEntity): ConversationSummary = ConversationSummary(
        id = row.conversationId,
        title = row.title,
        kind = enumValueOrDefault(row.kind, ConversationKind.SERVICE),
        serviceVertical = row.serviceVertical,
        serviceReferenceId = row.serviceReferenceId,
        requestState = enumValueOrDefault(row.requestState, MessageRequestState.ACCEPTED),
        lastActivityAtEpochMs = row.lastEventAtEpochMs,
        proofState = enumValueOrDefault(row.proofState, CommunicationProofState.MODEL_EXISTS),
    )

    private fun stableConversationId(owner: String, vertical: String, reference: String): String {
        val bytes = "$owner\u001f$vertical\u001f$reference".toByteArray(StandardCharsets.UTF_8)
        return "svc_" + MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(40)
    }

    private fun associatedData(conversationId: String, eventId: String, senderId: String): String =
        "elysium-communications-v1|$conversationId|$eventId|$senderId"

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
