package com.elysium369.meet.communications

enum class ContactDiscoveryMedium { ELYSIUM_ID, EMAIL, PHONE, QR, NEARBY }

enum class AliasProofState {
    VERIFIED,
    DECLARED,
    CACHED_VERIFIED,
    SERVER_ASSERTED,
    LOCAL_ONLY,
}

data class ElysiumIdentityProfile(
    val principalId: String,
    val elysiumId: String,
    val displayName: String,
    val about: String,
    val identityState: String,
    val email: String?,
    val emailVerificationState: String,
    val phone: String?,
    val phoneVerificationState: String,
)

data class CommunicationPrivacySettings(
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
)

data class ElysiumContact(
    val principalId: String,
    val elysiumId: String?,
    val displayName: String,
    val relationshipState: String,
    val aliasProofState: String,
    val isBlocked: Boolean = false,
)

data class ContactSearchResult(
    val principalId: String,
    val elysiumId: String,
    val displayName: String,
    val matchedMedium: ContactDiscoveryMedium,
    val aliasProofState: AliasProofState,
    val alreadyKnown: Boolean,
)

sealed interface ContactSearchOutcome {
    data class Found(val contacts: List<ContactSearchResult>) : ContactSearchOutcome
    data class NotFound(val normalizedQuery: String) : ContactSearchOutcome
    data object InvalidQuery : ContactSearchOutcome
    data object AuthenticationRequired : ContactSearchOutcome
    data object RateLimited : ContactSearchOutcome
    data object ServiceUnavailable : ContactSearchOutcome
}

sealed interface ContactRequestOutcome {
    data class Created(val conversationId: String) : ContactRequestOutcome
    data object AuthenticationRequired : ContactRequestOutcome
    data object Blocked : ContactRequestOutcome
    data object ServiceUnavailable : ContactRequestOutcome
}

data class BlockedContact(
    val principalId: String,
    val displayName: String,
    val syncState: String,
    val createdAtEpochMs: Long,
)

enum class MeshOperationState {
    OFF,
    PERMISSION_REQUIRED,
    STARTING,
    DISCOVERING,
    ACTIVE,
    UNSUPPORTED,
    FAILED,
}

data class MeshStatus(
    val state: MeshOperationState = MeshOperationState.OFF,
    val nearbyPeerCount: Int = 0,
    val relayMode: String = "OFF",
    val effectiveTransports: Set<String> = emptySet(),
    val honestDetail: String = "Mesh apagado",
)
