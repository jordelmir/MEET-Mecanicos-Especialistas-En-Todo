package com.elysium369.meet.core.billing

/**
 * MEET Global Platform — Canonical Capability Model.
 * Replaces ad-hoc `if (isPro)` checks with domain-level capability gating.
 * Enforces server-side authority for protected vehicular operations.
 */
enum class Capability {
    ADVANCED_DIAGNOSTICS,
    AI_DIAGNOSIS,
    MODE_06,
    PROFESSIONAL_REPORTS,
    WORKSHOP_WORKSPACE,
    SERVICE_LEADS,
    FLEET_TOOLS;

    companion object {
        fun fromKey(key: String): Capability? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

/**
 * Server-authoritative capability decision model.
 */
data class CapabilityDecision(
    val allowed: Boolean,
    val reason: Reason,
    val expiresAt: String? = null
) {
    enum class Reason {
        ENTITLED,
        NOT_ENTITLED,
        MARKET_DISABLED,
        ROLLOUT_DISABLED,
        PROVIDER_UNAVAILABLE,
        UNAUTHENTICATED
    }
}

/**
 * Authoritative capability repository contract.
 */
interface EntitlementRepository {
    suspend fun capabilities(): Set<Capability>
    suspend fun evaluateCapability(capability: Capability, marketCode: String? = null): CapabilityDecision
}
