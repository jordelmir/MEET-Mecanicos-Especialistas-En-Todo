package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Local encrypted projection of the universal communications domain.
 *
 * Plaintext message bodies are deliberately absent from every Room entity.
 */
@Entity(
    tableName = "communication_conversations",
    primaryKeys = ["conversationId", "ownerPrincipalId"],
    indices = [
        Index(value = ["ownerPrincipalId", "lastEventAtEpochMs"]),
        Index(
            value = ["ownerPrincipalId", "serviceVertical", "serviceReferenceId"],
            unique = true,
        ),
    ],
)
data class CommunicationConversationEntity(
    val conversationId: String,
    val ownerPrincipalId: String,
    val kind: String,
    val title: String,
    val serviceVertical: String? = null,
    val serviceReferenceId: String? = null,
    val requestState: String = "ACCEPTED",
    val proofState: String = "CLIENT_IMPLEMENTED",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastEventAtEpochMs: Long? = null,
)

@Entity(
    tableName = "communication_participants",
    primaryKeys = ["conversationId", "ownerPrincipalId", "participantPrincipalId"],
    foreignKeys = [
        ForeignKey(
            entity = CommunicationConversationEntity::class,
            parentColumns = ["conversationId", "ownerPrincipalId"],
            childColumns = ["conversationId", "ownerPrincipalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "ownerPrincipalId"]),
        Index(value = ["ownerPrincipalId", "participantPrincipalId"]),
    ],
)
data class CommunicationParticipantEntity(
    val conversationId: String,
    val ownerPrincipalId: String,
    val participantPrincipalId: String,
    val displayName: String,
    val role: String,
    val membershipState: String = "ACTIVE",
    val isVerifiedProvider: Boolean = false,
    val joinedAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "communication_events",
    foreignKeys = [
        ForeignKey(
            entity = CommunicationConversationEntity::class,
            parentColumns = ["conversationId", "ownerPrincipalId"],
            childColumns = ["conversationId", "ownerPrincipalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "ownerPrincipalId", "createdAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "syncState"]),
        Index(value = ["conversationId", "serverSequence"], unique = true),
        Index(value = ["conversationId", "ownerPrincipalId"]),
    ],
)
data class CommunicationEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val conversationId: String,
    val ownerPrincipalId: String,
    val senderPrincipalId: String,
    val senderDeviceId: String,
    val eventType: String,
    /** AES-GCM ciphertext for the local projection. Never plaintext. */
    val localCiphertextBase64: String,
    val localNonceBase64: String,
    /** Opaque E2EE transport envelope; null until a remote crypto adapter exists. */
    val remoteEnvelopeJson: String? = null,
    val replyToEventId: String? = null,
    val syncState: String = "LOCAL_ONLY",
    val serverSequence: Long? = null,
    val createdAtEpochMs: Long,
    val receivedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "communication_receipts",
    primaryKeys = ["eventId", "ownerPrincipalId", "readerPrincipalId", "receiptType"],
    indices = [Index(value = ["ownerPrincipalId", "conversationId"])],
)
data class CommunicationReceiptEntity(
    val eventId: String,
    val conversationId: String,
    val ownerPrincipalId: String,
    val readerPrincipalId: String,
    val receiptType: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "communication_calls",
    indices = [
        Index(value = ["ownerPrincipalId", "startedAtEpochMs"]),
        Index(value = ["conversationId"]),
    ],
)
data class CommunicationCallEntity(
    @androidx.room.PrimaryKey val callId: String,
    val conversationId: String,
    val ownerPrincipalId: String,
    val direction: String,
    val mediaType: String,
    val state: String,
    val transportProofState: String,
    val startedAtEpochMs: Long,
    val answeredAtEpochMs: Long? = null,
    val endedAtEpochMs: Long? = null,
    val failureCode: String? = null,
)
