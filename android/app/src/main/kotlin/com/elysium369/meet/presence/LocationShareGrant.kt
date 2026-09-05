package com.elysium369.meet.presence

/**
 * LocationShareGrant — Authoritative grant of presence visibility.
 *
 * Supreme Law:
 * A PERSON SHARES THEIR PRESENCE BY THEIR OWN DECISION,
 * FOR THE TIME, CONTEXT AND PRECISION THEY AUTHORIZE.
 * No Circle admin, map, remote service, or device may broaden that permission.
 */

enum class LocationShareMode {
    OFF,
    PLACE_ONLY,
    APPROXIMATE,
    PRECISE,
    JOURNEY_ONLY,
    TEMPORARY,
    EMERGENCY_ONLY;

    val allowsCoordinates: Boolean get() = this in listOf(APPROXIMATE, PRECISE, JOURNEY_ONLY, TEMPORARY, EMERGENCY_ONLY)
    val isPrecise: Boolean get() = this in listOf(PRECISE, TEMPORARY, EMERGENCY_ONLY)
}

enum class AudienceType {
    CIRCLE,
    INDIVIDUAL,
    RIDE,
    EMERGENCY
}

data class LocationShareGrant(
    val grantId: String,
    val ownerPrincipalId: String,
    val audienceType: AudienceType,
    val audienceId: String,
    val mode: LocationShareMode,
    val validFrom: Long,
    val validUntil: Long? = null,
    val shareBattery: Boolean = true,
    val shareConnectivity: Boolean = false,
    val shareMotion: Boolean = false,
    val shareJourney: Boolean = false,
    val shareVehicleProjection: Boolean = false,
    val version: Long = 1L,
    val createdBy: String = ownerPrincipalId,
) {
    init {
        // Enforce Self-Authority: Only the owner of the location can create or broaden the grant
        require(createdBy == ownerPrincipalId) {
            "Self-Authority violation: Only the location owner ($ownerPrincipalId) can create a LocationShareGrant (attempted by $createdBy)"
        }
        require(validFrom > 0L) { "validFrom timestamp required" }
        if (validUntil != null) {
            require(validUntil >= validFrom) { "validUntil cannot precede validFrom" }
        }
    }

    fun isValid(nowEpochMs: Long): Boolean {
        if (mode == LocationShareMode.OFF) return false
        if (nowEpochMs < validFrom) return false
        if (validUntil != null && nowEpochMs > validUntil) return false
        return true
    }

    /**
     * Prevents unauthorized escalation. A grant can only be modified by the owner.
     */
    fun updateMode(newMode: LocationShareMode, actorPrincipalId: String, newValidUntil: Long? = validUntil): LocationShareGrant {
        require(actorPrincipalId == ownerPrincipalId) {
            "Self-Authority violation: Circle admin or external actor ($actorPrincipalId) cannot modify share mode"
        }
        return copy(
            mode = newMode,
            validUntil = newValidUntil,
            version = version + 1,
        )
    }
}
