package com.elysium369.meet.identity

/**
 * Pure policy used by the Android auth gate. A local provisioning record is
 * continuity evidence only; it never grants cloud or provider authority.
 */
object PrincipalAccessPolicy {
    enum class SessionEvidence {
        LOADING,
        AUTHENTICATED,
        NOT_AUTHENTICATED,
        NETWORK_UNAVAILABLE,
    }

    enum class Decision {
        RESOLVING,
        REQUIRE_AUTHENTICATION,
        ENTER_AUTHENTICATED,
        ENTER_PROVISIONED_OFFLINE,
    }

    fun decide(
        session: SessionEvidence,
        provisionedPrincipalId: String?,
    ): Decision = when (session) {
        SessionEvidence.LOADING -> Decision.RESOLVING
        SessionEvidence.AUTHENTICATED -> Decision.ENTER_AUTHENTICATED
        SessionEvidence.NOT_AUTHENTICATED -> Decision.REQUIRE_AUTHENTICATION
        SessionEvidence.NETWORK_UNAVAILABLE -> if (provisionedPrincipalId.isNullOrBlank()) {
            Decision.REQUIRE_AUTHENTICATION
        } else {
            Decision.ENTER_PROVISIONED_OFFLINE
        }
    }
}

enum class PrincipalStatus {
    ACTIVE,
    SUSPENDED,
    DELETED,
    REVIEW_REQUIRED,
}

enum class CapabilityActivationState {
    NOT_REQUESTED,
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED,
    EXPIRED,
}

object PrincipalCapabilityPolicy {
    fun grantsProviderAuthority(
        principalAuthenticated: Boolean,
        principalStatus: PrincipalStatus,
        activationState: CapabilityActivationState,
    ): Boolean = principalAuthenticated &&
        principalStatus == PrincipalStatus.ACTIVE &&
        activationState == CapabilityActivationState.APPROVED
}

enum class PlatformAuthorityRole {
    PLATFORM_OWNER,
    SUPER_ADMIN,
    TRUST_REVIEWER,
    DRIVER_REVIEWER,
    PROVIDER_REVIEWER,
    LEGAL_REVIEWER,
    SUPPORT_ADMIN,
    FINANCE_ADMIN,
    OBSERVABILITY_ADMIN,
}

object PlatformAuthorityPolicy {
    fun clientMayAssignAuthority(): Boolean = false

    fun sensitiveActionAllowed(
        roles: Set<PlatformAuthorityRole>,
        authenticationAssuranceLevel: Int,
    ): Boolean = PlatformAuthorityRole.PLATFORM_OWNER in roles &&
        authenticationAssuranceLevel >= 2
}
