package com.elysium369.meet.core.identity

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   I D E N T I T Y   &   S C O P E   C O N T R A C T
 *  ──────────────────────────────────────────────────────────────
 *  Unified Principal -> Organization -> Capability -> Scope model.
 *  Decoupled from hardcoded roles. One human (e.g. Jorge) can hold
 *  multiple concurrent capabilities without account duplication.
 *
 *  Enforces strict multi-tenant isolation: knowing an external UUID
 *  NEVER grants access to another organization's telemetry/analytics.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class OrganizationType {
    FLEET,
    WORKSHOP,
    TOW_FLEET,
    PARTS_SUPPLIER,
    PLATFORM,
}

@Serializable
enum class MemberRole {
    OWNER,
    ADMIN,
    DISPATCHER,
    MEMBER,
    DRIVER,
    TECHNICIAN,
    OPERATOR,
}

@Serializable
enum class ActorCapability {
    REQUEST_RIDE,
    DRIVE_RIDE,

    REQUEST_REPAIR,
    PROVIDE_REPAIR,

    REQUEST_TOW,
    PROVIDE_TOW,

    MANAGE_FLEET,
    MANAGE_WORKSHOP,
    MANAGE_TOW_FLEET,

    VIEW_PERSONAL_ANALYTICS,
    VIEW_ORGANIZATION_ANALYTICS,

    MANAGE_TRUST,
    VIEW_PLATFORM_INTELLIGENCE,
}

@Serializable
enum class ScopeType {
    PERSONAL,
    PASSENGER,
    DRIVER,
    FLEET,
    WORKSHOP,
    TOW_FLEET,
    EXECUTIVE,
}

sealed interface Principal {
    val id: String
    val displayName: String

    @Serializable
    data class Person(
        override val id: String,
        val email: String,
        val phone: String,
        val fullName: String,
    ) : Principal {
        override val displayName: String get() = fullName.ifBlank { email }
    }

    @Serializable
    data class Organization(
        override val id: String,
        val legalName: String,
        val tradeName: String,
        val taxId: String,
        val type: OrganizationType,
    ) : Principal {
        override val displayName: String get() = tradeName.ifBlank { legalName }
    }
}

/**
 * Slowly Changing Dimension Type 2 (SCD2) historized membership.
 * Prevents rewriting history when a driver/asset moves from Fleet A to Fleet B.
 */
@Serializable
data class OrganizationMembership(
    val membershipId: String = UUID.randomUUID().toString(),
    val principalId: String,
    val organizationId: String,
    val role: MemberRole,
    val validFromEpochMs: Long,
    val validToEpochMs: Long? = null,
) {
    fun isActiveAt(epochMs: Long): Boolean {
        return epochMs >= validFromEpochMs && (validToEpochMs == null || epochMs <= validToEpochMs)
    }
}

/**
 * Authoritative scope governing access to Command Center analytics.
 * Enforces Zero-Trust tenant boundaries.
 */
@Serializable
data class AnalyticsScope(
    val principalId: String,
    val organizationId: String? = null,
    val scopeType: ScopeType,
    val subjectIds: Set<String> = emptySet(),
    val capabilities: Set<ActorCapability> = emptySet(),
) {
    /**
     * Strict multi-tenant isolation guard.
     * Prevents cross-tenant query leaks even if subject IDs are known.
     */
    fun canAccess(targetOrgId: String?, targetSubjectId: String?): Boolean {
        if (capabilities.contains(ActorCapability.VIEW_PLATFORM_INTELLIGENCE)) {
            return true // Executive root access
        }
        if (targetOrgId != null) {
            if (organizationId == null || organizationId != targetOrgId) {
                return false // Cross-tenant boundary violation
            }
            return capabilities.contains(ActorCapability.VIEW_ORGANIZATION_ANALYTICS)
        }
        if (targetSubjectId != null) {
            if (targetSubjectId == principalId || targetSubjectId in subjectIds) {
                return capabilities.contains(ActorCapability.VIEW_PERSONAL_ANALYTICS)
            }
            return false
        }
        return true
    }
}

/**
 * Authoritative scope governing access to Trust Center operations.
 */
@Serializable
data class TrustScope(
    val principalId: String,
    val organizationId: String? = null,
    val scopeType: ScopeType,
    val subjectIds: Set<String> = emptySet(),
    val canManageTrust: Boolean = false,
) {
    fun canInspect(targetSubjectId: String, targetOrgId: String?): Boolean {
        if (canManageTrust) return true
        if (targetOrgId != null && targetOrgId == organizationId) return true
        return targetSubjectId == principalId || targetSubjectId in subjectIds
    }
}
