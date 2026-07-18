package com.elysium369.meet.core.monetization

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val localCache = EntitlementLocalCache(context)
    
    private val _entitlements = MutableStateFlow<List<Entitlement>>(emptyList())
    val entitlements = _entitlements.asStateFlow()

    // Map of active rewarded unlocks with expiration times
    private val _rewardedGrants = MutableStateFlow<Map<FeatureKey, Long>>(emptyMap())
    val rewardedGrants = _rewardedGrants.asStateFlow()

    init {
        // Load cache on start
        _entitlements.value = localCache.getEntitlements()
    }

    fun hasAccess(feature: FeatureKey): Flow<Boolean> {
        return _entitlements.map { requireAccess(feature) is AccessDecision.Allowed }
    }

    fun requireAccess(feature: FeatureKey): AccessDecision {
        // Evaluate bypasses
        if (MonetizationPolicy.LOCAL_FULL_ACCESS) {
            return AccessDecision.Allowed
        }

        // Check if there is an active rewarded grant for this feature
        val rewardedExpiration = _rewardedGrants.value[feature]
        if (rewardedExpiration != null && rewardedExpiration > System.currentTimeMillis()) {
            return AccessDecision.Allowed
        }

        // Evaluate active entitlements
        val activeEntitlements = _entitlements.value.filter { 
            it.state == EntitlementState.ACTIVE && (it.expiresAt == null || it.expiresAt > System.currentTimeMillis())
        }

        when (feature) {
            FeatureKey.SCAN_BASIC, FeatureKey.DTC_BASIC -> {
                return AccessDecision.Allowed
            }
            FeatureKey.SCAN_ADVANCED, FeatureKey.DTC_EXPERT, FeatureKey.GAUGE_BUILDER_ADVANCED -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.REPORT_PDF_BASIC -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.PDF_REPORTS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedAdAvailable
            }
            FeatureKey.REPORT_PDF_CERTIFIED -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.PDF_CERTIFIED_REPORTS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.LIVELINK_30_MIN -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.LIVELINK_BASIC, EntitlementKey.LIVELINK_PRO)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedAdAvailable
            }
            FeatureKey.LIVELINK_PRO -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.LIVELINK_PRO)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.AI_DIAGNOSTIC_ADVANCED -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.AI_ADVANCED)) {
                    return AccessDecision.Allowed
                }
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.PRO_ACCESS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedAdAvailable
            }
            FeatureKey.GAUGE_MARKET_BUY -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.GAUGE_MARKET_ACCESS, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresPurchase
            }
            FeatureKey.GAUGE_MARKET_SELL -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.GAUGE_CREATOR_SELLING)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.OSCILLOSCOPE_USB -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.OSCILLOSCOPE_ADVANCED)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.SERVICE_RESET, FeatureKey.ACTIVE_TEST -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.BIDIRECTIONAL_SAFE_ACTIONS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.FLEET_DVIR -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.FLEET_ACCESS, EntitlementKey.DVIR_FLEET)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.FLEET_DASHBOARD -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.FLEET_ACCESS)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresSubscription
            }
            FeatureKey.MANUAL_RAG_OFFLINE -> {
                if (hasAnyEntitlement(activeEntitlements, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.MANUALS_OFFLINE)) {
                    return AccessDecision.Allowed
                }
                return AccessDecision.DeniedRequiresPurchase
            }
        }
    }

    private fun hasAnyEntitlement(activeList: List<Entitlement>, vararg keys: EntitlementKey): Boolean {
        return activeList.any { it.entitlementKey in keys }
    }

    fun consumeRewardedUnlock(feature: FeatureKey, durationMs: Long) {
        val expiration = System.currentTimeMillis() + durationMs
        val updated = _rewardedGrants.value.toMutableMap()
        updated[feature] = expiration
        _rewardedGrants.value = updated
        Log.i("EntitlementManager", "Rewarded unlock granted for $feature until $expiration")
    }

    fun grantLocalAccess(key: EntitlementKey, productId: String?, source: EntitlementSource = EntitlementSource.GOOGLE_PLAY_INAPP) {
        val now = System.currentTimeMillis()
        val newEntitlement = Entitlement(
            id = java.util.UUID.randomUUID().toString(),
            userId = null,
            entitlementKey = key,
            source = source,
            state = EntitlementState.ACTIVE,
            expiresAt = if (productId?.contains("monthly") == true) now + 30L * 24 * 60 * 60 * 1000 
                        else if (productId?.contains("yearly") == true) now + 365L * 24 * 60 * 60 * 1000 
                        else null,
            purchaseTokenHash = null,
            productId = productId,
            createdAt = now,
            updatedAt = now
        )
        val currentList = _entitlements.value.toMutableList()
        currentList.removeAll { it.entitlementKey == key }
        currentList.add(newEntitlement)
        _entitlements.value = currentList
        localCache.saveEntitlements(currentList)
    }

    fun revokeEntitlement(key: EntitlementKey) {
        val currentList = _entitlements.value.toMutableList()
        currentList.removeAll { it.entitlementKey == key }
        _entitlements.value = currentList
        localCache.saveEntitlements(currentList)
    }

    fun restorePurchases(mockRestore: Boolean = true) {
        // Implementation of local restore
        if (mockRestore) {
            grantLocalAccess(EntitlementKey.PRO_ACCESS, "pro_monthly", EntitlementSource.GOOGLE_PLAY_SUBSCRIPTION)
        }
    }

    fun clear() {
        _entitlements.value = emptyList()
        _rewardedGrants.value = emptyMap()
        localCache.clear()
    }
}
