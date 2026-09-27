package com.elysium369.meet.core.agentstore.domain

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   A G E N T   E N T I T L E M E N T   C O N T R A C T S
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §2, §3, §4
 *
 *  Fail-Closed Authority:
 *  - No local state synthesis.
 *  - UNKNOWN entitlement -> premium denied.
 *  - Backend unavailable -> premium operation denied.
 *  - Local patch / mock -> irrelevant against server verification.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
data class EntitlementSnapshot(
    val principalId: String,
    val entitlements: Set<String>,
    val asOfEpochMs: Long,
    val revision: Long,
)

sealed interface EntitlementState {
    data object Unknown : EntitlementState
    data class Available(
        val snapshot: EntitlementSnapshot,
    ) : EntitlementState
    data class Unavailable(
        val code: String,
    ) : EntitlementState
}

interface AgentEntitlementGateway {
    fun currentPrincipalId(): String? = null
    suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot>
}

@Serializable
data class AgentCommerceDescriptor(
    val storeProductId: String,
    val entitlementId: String,
)

interface AgentPurchaseCoordinator {
    suspend fun purchase(
        agentId: String,
        storeProductId: String,
    ): PurchaseOutcome
}

sealed interface PurchaseOutcome {
    data object Cancelled : PurchaseOutcome
    data class Pending(
        val purchaseTokenDigest: String,
    ) : PurchaseOutcome
    data class Verified(
        val entitlementId: String,
    ) : PurchaseOutcome
    data class Rejected(
        val code: String,
    ) : PurchaseOutcome
}
