package com.elysium369.meet.core.monetization

enum class EntitlementKey {
    PRO_ACCESS,
    ELITE_ACCESS,
    FLEET_ACCESS,
    ADS_REMOVED,
    PDF_REPORTS,
    PDF_CERTIFIED_REPORTS,
    LIVELINK_BASIC,
    LIVELINK_PRO,
    AI_ADVANCED,
    GAUGE_MARKET_ACCESS,
    GAUGE_CREATOR_SELLING,
    OSCILLOSCOPE_ADVANCED,
    BIDIRECTIONAL_SAFE_ACTIONS,
    MANUALS_OFFLINE,
    PERITO_REPORT,
    DVIR_FLEET
}

enum class EntitlementSource {
    GOOGLE_PLAY_SUBSCRIPTION,
    GOOGLE_PLAY_INAPP,
    ADMOB_REWARDED,
    ADMIN_GRANT,
    PROMO_CODE,
    MARKETPLACE_PURCHASE,
    TRIAL
}

enum class EntitlementState {
    ACTIVE,
    EXPIRED,
    REVOKED,
    REFUNDED,
    PENDING,
    GRACE_PERIOD,
    ON_HOLD
}

sealed class AccessDecision {
    object Allowed : AccessDecision()
    object DeniedRequiresSubscription : AccessDecision()
    object DeniedRequiresPurchase : AccessDecision()
    object DeniedAdAvailable : AccessDecision()
    object DeniedExpired : AccessDecision()
    object DeniedNetworkRequired : AccessDecision()
    object DeniedSafetyBlocked : AccessDecision()
}

data class Entitlement(
    val id: String,
    val userId: String?,
    val entitlementKey: EntitlementKey,
    val source: EntitlementSource,
    val state: EntitlementState,
    val expiresAt: Long?, // Epoch timestamp in ms, null if lifetime
    val purchaseTokenHash: String?,
    val productId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
