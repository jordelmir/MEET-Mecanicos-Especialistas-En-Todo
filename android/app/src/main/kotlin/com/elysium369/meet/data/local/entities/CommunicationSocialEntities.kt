package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/** Owner-scoped local projection of the public Elysium identity. */
@Entity(
    tableName = "communication_identity_profiles",
    indices = [Index(value = ["elysiumId"], unique = true)],
)
data class CommunicationIdentityProfileEntity(
    @androidx.room.PrimaryKey val ownerPrincipalId: String,
    val elysiumId: String,
    val displayName: String,
    val about: String = "",
    val identityState: String,
    val emailAliasCiphertextBase64: String? = null,
    val emailAliasNonceBase64: String? = null,
    val emailVerificationState: String = "ABSENT",
    val phoneAliasCiphertextBase64: String? = null,
    val phoneAliasNonceBase64: String? = null,
    val phoneVerificationState: String = "ABSENT",
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "communication_privacy_settings")
data class CommunicationPrivacySettingsEntity(
    @androidx.room.PrimaryKey val ownerPrincipalId: String,
    val findByElysiumId: String = "EVERYONE",
    val findByEmail: String = "NOBODY",
    val findByPhone: String = "NOBODY",
    val profilePhotoVisibility: String = "CONTACTS",
    val profileVisibility: String = "CONTACTS",
    val lastActiveVisibility: String = "CONTACTS",
    val onlineVisibility: String = "SAME_AS_LAST_ACTIVE",
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val callPermission: String = "CONTACTS",
    val groupInvitePermission: String = "CONTACTS",
    val meshDiscoverability: String = "OFF",
    val relayParticipation: String = "OFF",
    val relayOnlyWhileCharging: Boolean = false,
    val relayMinimumBatteryPercent: Int = 25,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "communication_relationships",
    primaryKeys = ["ownerPrincipalId", "peerPrincipalId"],
    indices = [
        Index(value = ["ownerPrincipalId", "relationshipState"]),
        Index(value = ["ownerPrincipalId", "peerElysiumId"]),
    ],
)
data class CommunicationRelationshipEntity(
    val ownerPrincipalId: String,
    val peerPrincipalId: String,
    val peerElysiumId: String?,
    val peerDisplayName: String,
    val relationshipState: String,
    val initiatedByMe: Boolean,
    val aliasProofState: String = "SERVER_ASSERTED",
    val emailLookupToken: String? = null,
    val phoneLookupToken: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "communication_local_blocks",
    primaryKeys = ["ownerPrincipalId", "blockedPrincipalId"],
    indices = [Index(value = ["ownerPrincipalId", "createdAtEpochMs"])],
)
data class CommunicationLocalBlockEntity(
    val ownerPrincipalId: String,
    val blockedPrincipalId: String,
    val blockedDisplayName: String,
    val reasonCode: String,
    val syncState: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "communication_presence_leases",
    primaryKeys = ["ownerPrincipalId", "peerPrincipalId"],
    indices = [Index(value = ["ownerPrincipalId", "expiresAtEpochMs"])],
)
data class CommunicationPresenceLeaseEntity(
    val ownerPrincipalId: String,
    val peerPrincipalId: String,
    val reachability: String,
    val source: String,
    val lastSeenAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Entity(
    tableName = "communication_mesh_peers",
    primaryKeys = ["ownerPrincipalId", "rotatingPeerId"],
    indices = [Index(value = ["ownerPrincipalId", "lastSeenAtEpochMs"])],
)
data class CommunicationMeshPeerEntity(
    val ownerPrincipalId: String,
    val rotatingPeerId: String,
    val transport: String,
    val capabilityFlags: Int,
    val trustState: String,
    val firstSeenAtEpochMs: Long,
    val lastSeenAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

@Entity(
    tableName = "communication_mesh_outbox",
    indices = [
        Index(value = ["ownerPrincipalId", "custodyState", "nextAttemptAtEpochMs"]),
        Index(value = ["ownerPrincipalId", "conversationId"]),
        Index(value = ["eventId"], unique = true),
    ],
)
data class CommunicationMeshOutboxEntity(
    @androidx.room.PrimaryKey val meshEnvelopeId: String,
    val ownerPrincipalId: String,
    val conversationId: String,
    val eventId: String,
    val destinationToken: String,
    val encryptedEnvelopeBase64: String,
    val priority: Int,
    val currentHopCount: Int,
    val maxHopCount: Int,
    val custodyState: String,
    val attemptCount: Int,
    val expiresAtEpochMs: Long,
    val nextAttemptAtEpochMs: Long,
    val createdAtEpochMs: Long,
)
