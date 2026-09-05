package com.elysium369.meet.circles

import com.elysium369.meet.presence.SharingGranularity

/**
 * Elysium Circles — Family safety and trusted presence domain.
 *
 * Supreme Laws:
 * - A PERSON SHARES THEIR PRESENCE BY THEIR OWN DECISION
 * - FOR THE TIME, CONTEXT AND PRECISION THEY AUTHORIZE
 * - No admin, remote actor or service may broaden or unpause another person's location.
 * - Realtime Authorization Epoch: Revoking membership increments accessEpoch, isolating previous sockets.
 */

enum class CircleMemberRole {
    OWNER,
    ADMIN,
    MEMBER,
    GUEST;

    val canManageMembers: Boolean get() = this in listOf(OWNER, ADMIN)
    val canRemoveMembers: Boolean get() = this in listOf(OWNER, ADMIN)
    val canDisbandCircle: Boolean get() = this == OWNER
    val canModifySettings: Boolean get() = this in listOf(OWNER, ADMIN)
}

enum class CircleMemberState {
    INVITED,
    PENDING_ACCEPTANCE,
    ACTIVE,
    PAUSED,
    LEFT,
    REVOKED,
    EXPIRED;

    val isSharing: Boolean get() = this == ACTIVE
}

enum class CircleType {
    FAMILY,
    CLOSE_FRIENDS,
    TRIP,
    WORK_GROUP,
    FLEET,
    WORKSHOP,
    TEMPORARY;

    val defaultGranularity: SharingGranularity get() = when (this) {
        FAMILY, CLOSE_FRIENDS, TRIP -> SharingGranularity.PRECISE
        WORK_GROUP, FLEET, WORKSHOP -> SharingGranularity.APPROXIMATE
        TEMPORARY -> SharingGranularity.PLACE_ONLY
    }

    val defaultAutoExpireMs: Long get() = when (this) {
        FAMILY -> 12 * 60 * 60 * 1000L // 12 hours
        CLOSE_FRIENDS -> 6 * 60 * 60 * 1000L // 6 hours
        TRIP -> 24 * 60 * 60 * 1000L
        WORK_GROUP -> 8 * 60 * 60 * 1000L // 8 hours
        FLEET -> 12 * 60 * 60 * 1000L
        WORKSHOP -> 8 * 60 * 60 * 1000L
        TEMPORARY -> 4 * 60 * 60 * 1000L // 4 hours
    }
}

data class Circle(
    val circleId: String,
    val name: String,
    val type: CircleType,
    val ownerPrincipalId: String,
    val createdAtEpochMs: Long,
    val memberCount: Int = 0,
    val isActive: Boolean = true,
    val accessEpoch: Long = 1L,
) {
    init {
        require(name.isNotBlank()) { "Circle name required" }
        require(name.length <= 100) { "Circle name too long" }
        require(accessEpoch >= 1L) { "accessEpoch must be positive" }
    }

    val broadcastTopic: String get() = "circle:$circleId:epoch:$accessEpoch"
}

data class CircleMember(
    val circleId: String,
    val principalId: String,
    val role: CircleMemberRole,
    val state: CircleMemberState,
    val displayName: String,
    val joinedAtEpochMs: Long,
    val invitedByPrincipalId: String,
    val sharingGranularity: SharingGranularity = SharingGranularity.PRECISE,
    val autoExpireMs: Long? = null,
    val lastSeenAtEpochMs: Long? = null,
) {
    fun isCurrentlySharing(): Boolean = state == CircleMemberState.ACTIVE
    fun canBeRemovedBy(actor: CircleMemberRole): Boolean = actor.canRemoveMembers && role != CircleMemberRole.OWNER
}

data class CircleInvitation(
    val invitationId: String,
    val circleId: String,
    val inviterPrincipalId: String,
    val inviteePrincipalId: String,
    val proposedRole: CircleMemberRole,
    val proposedGranularity: SharingGranularity,
    val tokenHashSha256: String = "",
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val acceptedAtEpochMs: Long? = null,
    val rejectedAtEpochMs: Long? = null,
) {
    fun isExpired(nowEpochMs: Long): Boolean = expiresAtEpochMs < nowEpochMs
    fun isPending(): Boolean = acceptedAtEpochMs == null && rejectedAtEpochMs == null
}

data class CircleConsentRecord(
    val consentId: String,
    val principalId: String,
    val circleId: String,
    val grantedGranularity: SharingGranularity,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null,
    val consentVersion: Int = 1,
    val consentHash: String,
) {
    fun isActive(nowEpochMs: Long): Boolean = revokedAtEpochMs == null && expiresAtEpochMs > nowEpochMs
}

object CirclePolicy {
    const val MAX_CIRCLES_PER_PERSON = 10
    const val MAX_MEMBERS_PER_CIRCLE = 25
    const val INVITATION_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    fun canInvite(role: CircleMemberRole): Boolean = role.canManageMembers

    fun validateGranularityForType(type: CircleType, granularity: SharingGranularity): Boolean = when (type) {
        CircleType.WORK_GROUP -> granularity != SharingGranularity.PRECISE
        else -> true
    }

    /**
     * Self-Authority: ONLY the location owner can modify their own sharing granularity.
     * Circle Admins can NEVER increase or force granularity changes on another member.
     */
    fun canModifyGranularity(
        actorPrincipalId: String,
        targetPrincipalId: String,
    ): Boolean = actorPrincipalId == targetPrincipalId
}
