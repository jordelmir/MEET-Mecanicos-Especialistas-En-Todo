package com.elysium369.meet.circles

import android.util.Log
import com.elysium369.meet.presence.PresenceGrantValidation
import com.elysium369.meet.presence.PresenceKernel
import com.elysium369.meet.presence.PresenceLocation
import com.elysium369.meet.presence.SharingGranularity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CircleKernel — Singleton authority for family safety circles.
 * Coordinates with PresenceKernel for location sharing.
 */
@Singleton
class CircleKernel @Inject constructor(
    private val presenceKernel: PresenceKernel,
) {
    private val circles = mutableMapOf<String, Circle>()
    private val members = mutableMapOf<String, MutableList<CircleMember>>()
    private val invitations = mutableMapOf<String, CircleInvitation>()
    private val consentRecords = mutableMapOf<String, CircleConsentRecord>()

    /** Create a new circle. */
    fun createCircle(
        name: String,
        type: CircleType,
        ownerPrincipalId: String,
    ): Circle {
        val circleId = UUID.randomUUID().toString()
        val circle = Circle(
            circleId = circleId,
            name = name,
            type = type,
            ownerPrincipalId = ownerPrincipalId,
            createdAtEpochMs = System.currentTimeMillis(),
            memberCount = 1,
        )
        circles[circleId] = circle

        val ownerMember = CircleMember(
            circleId = circleId,
            principalId = ownerPrincipalId,
            role = CircleMemberRole.OWNER,
            state = CircleMemberState.ACTIVE,
            displayName = "Owner",
            joinedAtEpochMs = System.currentTimeMillis(),
            invitedByPrincipalId = ownerPrincipalId,
            sharingGranularity = type.defaultGranularity,
            autoExpireMs = type.defaultAutoExpireMs,
        )
        members[circleId] = mutableListOf(ownerMember)

        Log.i("CircleKernel", "Created circle: $circleId ($name, $type)")
        return circle
    }

    /** Invite a member to a circle. */
    fun inviteMember(
        circleId: String,
        inviterPrincipalId: String,
        inviteePrincipalId: String,
        role: CircleMemberRole = CircleMemberRole.MEMBER,
        granularity: SharingGranularity? = null,
    ): CircleInvitationValidation {
        val circle = circles[circleId] ?: return CircleInvitationValidation.DENIED("Circle not found")
        val inviter = members[circleId]?.firstOrNull { it.principalId == inviterPrincipalId }
            ?: return CircleInvitationValidation.DENIED("Inviter not a member")
        if (!CirclePolicy.canInvite(inviter.role)) {
            return CircleInvitationValidation.DENIED("Insufficient permissions")
        }
        val currentMembers = members[circleId]?.size ?: 0
        if (currentMembers >= CirclePolicy.MAX_MEMBERS_PER_CIRCLE) {
            return CircleInvitationValidation.DENIED("Circle is full")
        }
        if (members[circleId]?.any { it.principalId == inviteePrincipalId } == true) {
            return CircleInvitationValidation.DENIED("Already a member")
        }

        val effectiveGranularity = granularity ?: circle.type.defaultGranularity
        if (!CirclePolicy.validateGranularityForType(circle.type, effectiveGranularity)) {
            return CircleInvitationValidation.DENIED("Granularity not allowed for this circle type")
        }

        val invitation = CircleInvitation(
            invitationId = UUID.randomUUID().toString(),
            circleId = circleId,
            inviterPrincipalId = inviterPrincipalId,
            inviteePrincipalId = inviteePrincipalId,
            proposedRole = role,
            proposedGranularity = effectiveGranularity,
            createdAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + CirclePolicy.INVITATION_EXPIRY_MS,
        )
        invitations[invitation.invitationId] = invitation

        Log.i("CircleKernel", "Invited $inviteePrincipalId to circle $circleId")
        return CircleInvitationValidation.ACCEPTED(invitation.invitationId)
    }

    /** Accept a circle invitation. */
    fun acceptInvitation(
        invitationId: String,
        acceptorPrincipalId: String,
    ): CircleInvitationAcceptance {
        val invitation = invitations[invitationId]
            ?: return CircleInvitationAcceptance.DENIED("Invitation not found")
        if (invitation.inviteePrincipalId != acceptorPrincipalId) {
            return CircleInvitationAcceptance.DENIED("Not your invitation")
        }
        if (!invitation.isPending()) {
            return CircleInvitationAcceptance.DENIED("Invitation already processed")
        }

        val updatedInvitation = invitation.copy(acceptedAtEpochMs = System.currentTimeMillis())
        invitations[invitationId] = updatedInvitation

        val member = CircleMember(
            circleId = invitation.circleId,
            principalId = acceptorPrincipalId,
            role = invitation.proposedRole,
            state = CircleMemberState.ACTIVE,
            displayName = acceptorPrincipalId.take(8),
            joinedAtEpochMs = System.currentTimeMillis(),
            invitedByPrincipalId = invitation.inviterPrincipalId,
            sharingGranularity = invitation.proposedGranularity,
            autoExpireMs = circles[invitation.circleId]?.type?.defaultAutoExpireMs,
        )
        members.getOrPut(invitation.circleId) { mutableListOf() }.add(member)

        // Update circle member count
        circles[invitation.circleId]?.let { circle ->
            circles[invitation.circleId] = circle.copy(memberCount = (members[invitation.circleId]?.size ?: 0))
        }

        // Record consent
        val consent = CircleConsentRecord(
            consentId = UUID.randomUUID().toString(),
            principalId = acceptorPrincipalId,
            circleId = invitation.circleId,
            grantedGranularity = invitation.proposedGranularity,
            grantedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = invitation.expiresAtEpochMs,
            consentHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest("$acceptorPrincipalId:${invitation.circleId}:${System.currentTimeMillis()}".toByteArray())
                .joinToString("") { "%02x".format(it) },
        )
        consentRecords[consent.consentId] = consent

        Log.i("CircleKernel", "$acceptorPrincipalId joined circle ${invitation.circleId}")
        return CircleInvitationAcceptance.ACCEPTED
    }

    /** Update member's location via PresenceKernel. */
    fun updateMemberLocation(
        circleId: String,
        principalId: String,
        location: PresenceLocation,
    ) {
        val member = members[circleId]?.firstOrNull { it.principalId == principalId }
        if (member == null || !member.isCurrentlySharing()) return

        presenceKernel.startSharing(
            principalId = principalId,
            location = location,
            granularity = member.sharingGranularity,
        )
    }

    /** Get all circles a principal belongs to. */
    fun getCirclesForPrincipal(principalId: String): List<Circle> {
        return circles.values.filter { circle ->
            members[circle.circleId]?.any {
                it.principalId == principalId && it.state == CircleMemberState.ACTIVE
            } == true
        }
    }

    /** Get members of a circle. */
    fun getMembers(circleId: String): List<CircleMember> {
        return members[circleId]?.toList() ?: emptyList()
    }

    /** Get location of a circle member (via PresenceKernel). */
    fun getMemberLocation(
        circleId: String,
        memberPrincipalId: String,
        consumerPrincipalId: String,
    ): PresenceLocation? {
        return presenceKernel.getLocationForConsumer(memberPrincipalId, consumerPrincipalId)
    }

    /** Remove a member from a circle. */
    fun removeMember(
        circleId: String,
        actorPrincipalId: String,
        targetPrincipalId: String,
    ): Boolean {
        val actor = members[circleId]?.firstOrNull { it.principalId == actorPrincipalId }
            ?: return false
        val target = members[circleId]?.firstOrNull { it.principalId == targetPrincipalId }
            ?: return false
        if (!target.canBeRemovedBy(actor.role)) return false

        members[circleId]?.removeAll { it.principalId == targetPrincipalId }
        presenceKernel.stopSharing(targetPrincipalId)

        Log.i("CircleKernel", "Removed $targetPrincipalId from circle $circleId")
        return true
    }

    /** Disband a circle (owner only). */
    fun disbandCircle(circleId: String, ownerPrincipalId: String): Boolean {
        val circle = circles[circleId] ?: return false
        if (circle.ownerPrincipalId != ownerPrincipalId) return false

        members[circleId]?.forEach { member ->
            presenceKernel.stopSharing(member.principalId)
        }
        circles.remove(circleId)
        members.remove(circleId)

        Log.i("CircleKernel", "Disbanded circle $circleId")
        return true
    }

    /** Cleanup expired invitations and grants. */
    fun cleanup() {
        val now = System.currentTimeMillis()
        invitations.entries.removeIf { (_, inv) -> inv.isExpired(now) }
        consentRecords.entries.removeIf { (_, consent) -> !consent.isActive(now) }
    }
}

sealed interface CircleInvitationValidation {
    data object ALLOWED : CircleInvitationValidation
    data class ACCEPTED(val invitationId: String) : CircleInvitationValidation
    data class DENIED(val reason: String) : CircleInvitationValidation
}

sealed interface CircleInvitationAcceptance {
    data object ACCEPTED : CircleInvitationAcceptance
    data class DENIED(val reason: String) : CircleInvitationAcceptance
}
