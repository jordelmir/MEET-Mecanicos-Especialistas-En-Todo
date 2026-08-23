package com.elysium369.meet.communications

import com.elysium369.meet.data.local.dao.CommunicationDao
import com.elysium369.meet.data.local.entities.CommunicationCallEntity
import com.elysium369.meet.data.local.entities.CommunicationConversationEntity
import com.elysium369.meet.data.local.entities.CommunicationEventEntity
import com.elysium369.meet.data.local.entities.CommunicationParticipantEntity
import com.elysium369.meet.data.local.entities.CommunicationIdentityProfileEntity
import com.elysium369.meet.data.local.entities.CommunicationLocalBlockEntity
import com.elysium369.meet.data.local.entities.CommunicationPrivacySettingsEntity
import com.elysium369.meet.data.local.entities.CommunicationRelationshipEntity
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
    private val aliasMatcher: DeviceAliasMatcher,
) {
    val callState = callTransport.state
    val conversations: Flow<List<ConversationSummary>> =
        principalKernel.activePrincipal.flatMapLatest { principal ->
            dao.observeConversations(principal.id).map { rows -> rows.map(::toSummary) }
        }

    val identity: Flow<ElysiumIdentityProfile> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeIdentityProfile(principal.id).map { row -> row?.toIdentity() ?: defaultIdentity(principal.id) }
    }

    val privacy: Flow<CommunicationPrivacySettings> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observePrivacySettings(principal.id).map { row -> row?.toPrivacy() ?: CommunicationPrivacySettings() }
    }

    val contacts: Flow<List<ElysiumContact>> = principalKernel.activePrincipal.flatMapLatest { principal ->
        combine(
            dao.observeRelationships(principal.id),
            dao.observeBlocks(principal.id),
        ) { relationships, blocks ->
            val blocked = blocks.mapTo(mutableSetOf()) { it.blockedPrincipalId }
            relationships.map { row ->
                ElysiumContact(
                    principalId = row.peerPrincipalId,
                    elysiumId = row.peerElysiumId,
                    displayName = row.peerDisplayName,
                    relationshipState = row.relationshipState,
                    aliasProofState = row.aliasProofState,
                    isBlocked = row.peerPrincipalId in blocked,
                )
            }
        }
    }

    val blockedContacts: Flow<List<BlockedContact>> = principalKernel.activePrincipal.flatMapLatest { principal ->
        dao.observeBlocks(principal.id).map { rows ->
            rows.map { BlockedContact(it.blockedPrincipalId, it.blockedDisplayName, it.syncState, it.createdAtEpochMs) }
        }
    }

    suspend fun initializeSocialDefaults() {
        val principal = principalKernel.current()
        val now = System.currentTimeMillis()
        if (dao.getIdentityProfile(principal.id) == null) {
            val fallback = defaultIdentity(principal.id)
            dao.upsertIdentityProfile(
                CommunicationIdentityProfileEntity(
                    ownerPrincipalId = principal.id,
                    elysiumId = fallback.elysiumId,
                    displayName = fallback.displayName,
                    about = fallback.about,
                    identityState = fallback.identityState,
                    updatedAtEpochMs = now,
                ),
            )
        }
        if (dao.getPrivacySettings(principal.id) == null) {
            dao.upsertPrivacySettings(CommunicationPrivacySettings().toEntity(principal.id, now))
        }
    }

    suspend fun searchContact(query: String): ContactSearchOutcome {
        val parsed = parseDiscoveryQuery(query) ?: return ContactSearchOutcome.InvalidQuery
        val principal = principalKernel.current()
        val local = when (parsed.first) {
            ContactDiscoveryMedium.ELYSIUM_ID -> dao.findRelationshipByElysiumId(principal.id, parsed.second)
            ContactDiscoveryMedium.EMAIL -> dao.findRelationshipByEmailToken(principal.id, aliasMatcher.tag(parsed.first, parsed.second))
            ContactDiscoveryMedium.PHONE -> dao.findRelationshipByPhoneToken(principal.id, aliasMatcher.tag(parsed.first, parsed.second))
            else -> null
        }
        if (local != null && !dao.isBlocked(principal.id, local.peerPrincipalId)) {
            return ContactSearchOutcome.Found(listOf(local.toSearchResult(parsed.first, alreadyKnown = true)))
        }
        return when (val remote = remoteGateway.lookupExact(parsed.first, parsed.second)) {
            is RemoteDiscoveryOutcome.Found -> {
                val now = System.currentTimeMillis()
                val contacts = remote.contacts.mapNotNull { row ->
                    if (dao.isBlocked(principal.id, row.principalId)) return@mapNotNull null
                    val proof = enumValueOrDefault(row.aliasProofState, AliasProofState.SERVER_ASSERTED)
                    val relationship = CommunicationRelationshipEntity(
                        ownerPrincipalId = principal.id,
                        peerPrincipalId = row.principalId,
                        peerElysiumId = row.elysiumId,
                        peerDisplayName = row.displayName,
                        relationshipState = "DISCOVERED",
                        initiatedByMe = false,
                        aliasProofState = proof.name,
                        emailLookupToken = if (parsed.first == ContactDiscoveryMedium.EMAIL) aliasMatcher.tag(parsed.first, parsed.second) else null,
                        phoneLookupToken = if (parsed.first == ContactDiscoveryMedium.PHONE) aliasMatcher.tag(parsed.first, parsed.second) else null,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    )
                    dao.upsertRelationship(relationship)
                    relationship.toSearchResult(parsed.first, alreadyKnown = false)
                }
                if (contacts.isEmpty()) ContactSearchOutcome.NotFound(parsed.second) else ContactSearchOutcome.Found(contacts)
            }
            RemoteDiscoveryOutcome.AuthenticationRequired -> ContactSearchOutcome.AuthenticationRequired
            RemoteDiscoveryOutcome.RateLimited -> ContactSearchOutcome.RateLimited
            RemoteDiscoveryOutcome.Unavailable -> ContactSearchOutcome.ServiceUnavailable
        }
    }

    suspend fun requestContact(contact: ContactSearchResult): ContactRequestOutcome {
        val principal = principalKernel.current()
        if (dao.isBlocked(principal.id, contact.principalId)) return ContactRequestOutcome.Blocked
        if (!principal.isAuthenticated) return ContactRequestOutcome.AuthenticationRequired
        val conversationId = remoteGateway.createDirectRequest(contact.principalId)
            ?: return ContactRequestOutcome.ServiceUnavailable
        val now = System.currentTimeMillis()
        runCatching {
            dao.insertConversation(
                CommunicationConversationEntity(
                    conversationId = conversationId,
                    ownerPrincipalId = principal.id,
                    kind = ConversationKind.DIRECT.name,
                    title = contact.displayName,
                    requestState = MessageRequestState.PENDING.name,
                    proofState = CommunicationProofState.SERVER_AUTHORITATIVE.name,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        dao.upsertParticipant(CommunicationParticipantEntity(conversationId, principal.id, principal.id, "Tú", "MEMBER", joinedAtEpochMs = now))
        dao.upsertParticipant(CommunicationParticipantEntity(conversationId, principal.id, contact.principalId, contact.displayName, "MEMBER", joinedAtEpochMs = now))
        dao.upsertRelationship(
            CommunicationRelationshipEntity(
                ownerPrincipalId = principal.id,
                peerPrincipalId = contact.principalId,
                peerElysiumId = contact.elysiumId,
                peerDisplayName = contact.displayName,
                relationshipState = "PENDING",
                initiatedByMe = true,
                aliasProofState = contact.aliasProofState.name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        return ContactRequestOutcome.Created(conversationId)
    }

    suspend fun saveIdentity(
        elysiumId: String,
        displayName: String,
        about: String,
        phone: String?,
    ): Boolean {
        val principal = principalKernel.current()
        val id = elysiumId.trim().removePrefix("@").lowercase()
        require(id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) { "INVALID_ELYSIUM_ID" }
        require(displayName.trim().length in 1..120) { "INVALID_DISPLAY_NAME" }
        val normalizedPhone = phone?.let(::normalizePhone)?.takeIf(String::isNotBlank)
        val encryptedPhone = normalizedPhone?.let {
            cipher.encrypt(it, identityAliasAad(principal.id, ContactDiscoveryMedium.PHONE))
        }
        val current = dao.getIdentityProfile(principal.id)
        val remoteSaved = principal.isAuthenticated && remoteGateway.ensureIdentity(
            id,
            displayName.trim(),
            about.trim().take(280),
            normalizedPhone,
            dao.getPrivacySettings(principal.id)?.findByPhone ?: "NOBODY",
        )
        dao.upsertIdentityProfile(
            CommunicationIdentityProfileEntity(
                ownerPrincipalId = principal.id,
                elysiumId = id,
                displayName = displayName.trim(),
                about = about.trim().take(280),
                identityState = if (remoteSaved) "SERVER_AUTHORITATIVE" else "LOCAL_ONLY",
                emailAliasCiphertextBase64 = current?.emailAliasCiphertextBase64,
                emailAliasNonceBase64 = current?.emailAliasNonceBase64,
                emailVerificationState = current?.emailVerificationState ?: "ABSENT",
                phoneAliasCiphertextBase64 = encryptedPhone?.ciphertextBase64,
                phoneAliasNonceBase64 = encryptedPhone?.nonceBase64,
                phoneVerificationState = if (normalizedPhone == null) "ABSENT" else "DECLARED",
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return remoteSaved
    }

    suspend fun savePrivacy(settings: CommunicationPrivacySettings): Boolean {
        val principal = principalKernel.current()
        val safe = settings.copy(relayMinimumBatteryPercent = settings.relayMinimumBatteryPercent.coerceIn(10, 90))
        dao.upsertPrivacySettings(safe.toEntity(principal.id, System.currentTimeMillis()))
        return principal.isAuthenticated && remoteGateway.savePrivacy(safe)
    }

    suspend fun blockContact(contact: ElysiumContact): Boolean {
        val principal = principalKernel.current()
        val synced = principal.isAuthenticated && remoteGateway.blockPrincipal(contact.principalId)
        dao.upsertBlock(
            CommunicationLocalBlockEntity(
                ownerPrincipalId = principal.id,
                blockedPrincipalId = contact.principalId,
                blockedDisplayName = contact.displayName,
                reasonCode = "USER_REQUESTED",
                syncState = if (synced) "SYNCED" else "LOCAL_ONLY",
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return synced
    }

    suspend fun unblockContact(contact: BlockedContact): Boolean {
        val principal = principalKernel.current()
        val synced = !principal.isAuthenticated || remoteGateway.unblockPrincipal(contact.principalId)
        if (synced) dao.deleteBlock(principal.id, contact.principalId)
        return synced
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
        if (dao.activePeerPrincipalIds(conversationId, principal.id).any { dao.isBlocked(principal.id, it) }) {
            return SendMessageOutcome.ConversationUnavailable
        }
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
        if (dao.activePeerPrincipalIds(conversationId, principal.id).any { dao.isBlocked(principal.id, it) }) {
            return StartCallOutcome.Failed("CONTACT_BLOCKED")
        }
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

    private fun identityAliasAad(ownerPrincipalId: String, medium: ContactDiscoveryMedium): String =
        "elysium-identity-v1|$ownerPrincipalId|${medium.name}"

    private fun defaultIdentity(principalId: String): ElysiumIdentityProfile {
        val suffix = MessageDigest.getInstance("SHA-256")
            .digest(principalId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(8)
        return ElysiumIdentityProfile(
            principalId = principalId,
            elysiumId = "elysium-$suffix",
            displayName = "Usuario Elysium",
            about = "",
            identityState = "LOCAL_ONLY",
            email = null,
            emailVerificationState = "ABSENT",
            phone = null,
            phoneVerificationState = "ABSENT",
        )
    }

    private fun CommunicationIdentityProfileEntity.toIdentity(): ElysiumIdentityProfile {
        fun decryptAlias(ciphertext: String?, nonce: String?, medium: ContactDiscoveryMedium): String? {
            if (ciphertext == null || nonce == null) return null
            return runCatching {
                cipher.decrypt(LocalCipherPayload(ciphertext, nonce), identityAliasAad(ownerPrincipalId, medium))
            }.getOrNull()
        }
        return ElysiumIdentityProfile(
            principalId = ownerPrincipalId,
            elysiumId = elysiumId,
            displayName = displayName,
            about = about,
            identityState = identityState,
            email = decryptAlias(emailAliasCiphertextBase64, emailAliasNonceBase64, ContactDiscoveryMedium.EMAIL),
            emailVerificationState = emailVerificationState,
            phone = decryptAlias(phoneAliasCiphertextBase64, phoneAliasNonceBase64, ContactDiscoveryMedium.PHONE),
            phoneVerificationState = phoneVerificationState,
        )
    }

    private fun CommunicationPrivacySettingsEntity.toPrivacy() = CommunicationPrivacySettings(
        findByElysiumId, findByEmail, findByPhone, profilePhotoVisibility, profileVisibility,
        lastActiveVisibility, onlineVisibility, readReceiptsEnabled, typingIndicatorsEnabled,
        callPermission, groupInvitePermission, meshDiscoverability, relayParticipation,
        relayOnlyWhileCharging, relayMinimumBatteryPercent,
    )

    private fun CommunicationPrivacySettings.toEntity(owner: String, now: Long) = CommunicationPrivacySettingsEntity(
        owner, findByElysiumId, findByEmail, findByPhone, profilePhotoVisibility, profileVisibility,
        lastActiveVisibility, onlineVisibility, readReceiptsEnabled, typingIndicatorsEnabled,
        callPermission, groupInvitePermission, meshDiscoverability, relayParticipation,
        relayOnlyWhileCharging, relayMinimumBatteryPercent, now,
    )

    private fun CommunicationRelationshipEntity.toSearchResult(medium: ContactDiscoveryMedium, alreadyKnown: Boolean) =
        ContactSearchResult(
            peerPrincipalId,
            peerElysiumId.orEmpty(),
            peerDisplayName,
            medium,
            enumValueOrDefault(aliasProofState, AliasProofState.SERVER_ASSERTED),
            alreadyKnown,
        )

    private fun parseDiscoveryQuery(raw: String): Pair<ContactDiscoveryMedium, String>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("@")) {
            val id = trimmed.removePrefix("@").lowercase()
            return if (id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) ContactDiscoveryMedium.ELYSIUM_ID to id else null
        }
        if (trimmed.contains('@')) {
            val email = trimmed.lowercase()
            return if (email.length <= 320 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) ContactDiscoveryMedium.EMAIL to email else null
        }
        val phone = normalizePhone(trimmed)
        if (phone.matches(Regex("^\\+?[0-9]{7,15}$"))) return ContactDiscoveryMedium.PHONE to phone
        val id = trimmed.lowercase()
        return if (id.matches(Regex("^[a-z0-9][a-z0-9._-]{2,31}$"))) ContactDiscoveryMedium.ELYSIUM_ID to id else null
    }

    private fun normalizePhone(raw: String): String = raw.trim().replace(Regex("[^0-9+]"), "")

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
