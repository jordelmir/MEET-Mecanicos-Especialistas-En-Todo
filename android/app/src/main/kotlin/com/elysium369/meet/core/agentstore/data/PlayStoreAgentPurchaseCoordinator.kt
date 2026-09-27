package com.elysium369.meet.core.agentstore.data

import com.elysium369.meet.core.agentstore.domain.AgentPurchaseCoordinator
import com.elysium369.meet.core.agentstore.domain.PurchaseOutcome
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of AgentPurchaseCoordinator.
 * Governed by ASCENSION §3:
 * The UI initiates a purchase; unlocking ONLY occurs after server verification.
 * Local state mutation to grant entitlement without server verification is strictly prohibited.
 */
@Singleton
class PlayStoreAgentPurchaseCoordinator @Inject constructor(
    private val entitlementRepository: AgentEntitlementRepository,
) : AgentPurchaseCoordinator {

    private val logger = LoggerFactory.getLogger(PlayStoreAgentPurchaseCoordinator::class.java)

    override suspend fun purchase(agentId: String, storeProductId: String): PurchaseOutcome {
        // No purchase is pending until a real store supplies a verifiable receipt.
        return PurchaseOutcome.Rejected("BILLING_NOT_CONFIGURED")
    }
}
