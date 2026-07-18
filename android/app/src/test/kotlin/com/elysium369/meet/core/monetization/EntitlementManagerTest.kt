package com.elysium369.meet.core.monetization

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-logic unit tests for the monetization domain models.
 * No Android Context required — tests the enums, data classes,
 * and decision matrix directly.
 */
class EntitlementManagerTest {

    // ── Helper: simulate requireAccess logic as a pure function ──
    private fun evaluateAccess(
        feature: FeatureKey,
        activeKeys: Set<EntitlementKey>,
        rewardedGrants: Map<FeatureKey, Long> = emptyMap(),
        localFullAccess: Boolean = false
    ): AccessDecision {
        if (localFullAccess) return AccessDecision.Allowed

        val rewardedExpiration = rewardedGrants[feature]
        if (rewardedExpiration != null && rewardedExpiration > System.currentTimeMillis()) {
            return AccessDecision.Allowed
        }

        return when (feature) {
            FeatureKey.SCAN_BASIC, FeatureKey.DTC_BASIC ->
                AccessDecision.Allowed

            FeatureKey.SCAN_ADVANCED, FeatureKey.DTC_EXPERT, FeatureKey.GAUGE_BUILDER_ADVANCED ->
                if (activeKeys.intersect(setOf(EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.REPORT_PDF_BASIC ->
                if (activeKeys.intersect(setOf(EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.PDF_REPORTS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedAdAvailable

            FeatureKey.REPORT_PDF_CERTIFIED ->
                if (activeKeys.intersect(setOf(EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.PDF_CERTIFIED_REPORTS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.LIVELINK_30_MIN ->
                if (activeKeys.intersect(setOf(EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.LIVELINK_BASIC, EntitlementKey.LIVELINK_PRO)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedAdAvailable

            FeatureKey.LIVELINK_PRO ->
                if (activeKeys.intersect(setOf(EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.LIVELINK_PRO)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.AI_DIAGNOSTIC_ADVANCED ->
                if (activeKeys.intersect(setOf(EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.AI_ADVANCED, EntitlementKey.PRO_ACCESS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedAdAvailable

            FeatureKey.GAUGE_MARKET_BUY ->
                if (activeKeys.intersect(setOf(EntitlementKey.GAUGE_MARKET_ACCESS, EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresPurchase

            FeatureKey.GAUGE_MARKET_SELL ->
                if (EntitlementKey.GAUGE_CREATOR_SELLING in activeKeys)
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.OSCILLOSCOPE_USB ->
                if (activeKeys.intersect(setOf(EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.OSCILLOSCOPE_ADVANCED)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.SERVICE_RESET, FeatureKey.ACTIVE_TEST ->
                if (activeKeys.intersect(setOf(EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.BIDIRECTIONAL_SAFE_ACTIONS)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.FLEET_DVIR ->
                if (activeKeys.intersect(setOf(EntitlementKey.FLEET_ACCESS, EntitlementKey.DVIR_FLEET)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.FLEET_DASHBOARD ->
                if (EntitlementKey.FLEET_ACCESS in activeKeys)
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresSubscription

            FeatureKey.MANUAL_RAG_OFFLINE ->
                if (activeKeys.intersect(setOf(EntitlementKey.PRO_ACCESS, EntitlementKey.ELITE_ACCESS, EntitlementKey.FLEET_ACCESS, EntitlementKey.MANUALS_OFFLINE)).isNotEmpty())
                    AccessDecision.Allowed else AccessDecision.DeniedRequiresPurchase
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LOCAL_FULL_ACCESS bypass
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `LOCAL_FULL_ACCESS bypasses all gates`() {
        FeatureKey.entries.forEach { feature ->
            val decision = evaluateAccess(feature, emptySet(), localFullAccess = true)
            assertTrue("$feature should be Allowed with LOCAL_FULL_ACCESS", decision is AccessDecision.Allowed)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // FREE tier (no entitlements)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `FREE user can access basic scan and DTC`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.SCAN_BASIC, emptySet()))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.DTC_BASIC, emptySet()))
    }

    @Test
    fun `FREE user is denied advanced scan`() {
        val decision = evaluateAccess(FeatureKey.SCAN_ADVANCED, emptySet())
        assertTrue(decision is AccessDecision.DeniedRequiresSubscription)
    }

    @Test
    fun `FREE user gets DeniedAdAvailable for PDF basic`() {
        val decision = evaluateAccess(FeatureKey.REPORT_PDF_BASIC, emptySet())
        assertTrue(decision is AccessDecision.DeniedAdAvailable)
    }

    @Test
    fun `FREE user is hard-denied certified PDF`() {
        val decision = evaluateAccess(FeatureKey.REPORT_PDF_CERTIFIED, emptySet())
        assertTrue(decision is AccessDecision.DeniedRequiresSubscription)
    }

    @Test
    fun `FREE user is hard-denied oscilloscope`() {
        val decision = evaluateAccess(FeatureKey.OSCILLOSCOPE_USB, emptySet())
        assertTrue(decision is AccessDecision.DeniedRequiresSubscription)
    }

    @Test
    fun `FREE user is hard-denied service reset and active test`() {
        assertTrue(evaluateAccess(FeatureKey.SERVICE_RESET, emptySet()) is AccessDecision.DeniedRequiresSubscription)
        assertTrue(evaluateAccess(FeatureKey.ACTIVE_TEST, emptySet()) is AccessDecision.DeniedRequiresSubscription)
    }

    // ═══════════════════════════════════════════════════════════
    // PRO tier
    // ═══════════════════════════════════════════════════════════

    private val proKeys = setOf(EntitlementKey.PRO_ACCESS)

    @Test
    fun `PRO user can access advanced scan and DTC expert`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.SCAN_ADVANCED, proKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.DTC_EXPERT, proKeys))
    }

    @Test
    fun `PRO user can access basic PDF and AI`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.REPORT_PDF_BASIC, proKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.AI_DIAGNOSTIC_ADVANCED, proKeys))
    }

    @Test
    fun `PRO user is denied certified PDF`() {
        assertTrue(evaluateAccess(FeatureKey.REPORT_PDF_CERTIFIED, proKeys) is AccessDecision.DeniedRequiresSubscription)
    }

    @Test
    fun `PRO user is denied oscilloscope`() {
        assertTrue(evaluateAccess(FeatureKey.OSCILLOSCOPE_USB, proKeys) is AccessDecision.DeniedRequiresSubscription)
    }

    // ═══════════════════════════════════════════════════════════
    // ELITE tier
    // ═══════════════════════════════════════════════════════════

    private val eliteKeys = setOf(EntitlementKey.ELITE_ACCESS)

    @Test
    fun `ELITE user can access everything except fleet-exclusive`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.SCAN_ADVANCED, eliteKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.REPORT_PDF_CERTIFIED, eliteKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.OSCILLOSCOPE_USB, eliteKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.SERVICE_RESET, eliteKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.ACTIVE_TEST, eliteKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.LIVELINK_PRO, eliteKeys))
    }

    @Test
    fun `ELITE user is denied fleet dashboard without fleet entitlement`() {
        assertTrue(evaluateAccess(FeatureKey.FLEET_DASHBOARD, eliteKeys) is AccessDecision.DeniedRequiresSubscription)
    }

    // ═══════════════════════════════════════════════════════════
    // FLEET tier
    // ═══════════════════════════════════════════════════════════

    private val fleetKeys = setOf(EntitlementKey.FLEET_ACCESS)

    @Test
    fun `FLEET user can access fleet features`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.FLEET_DVIR, fleetKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.FLEET_DASHBOARD, fleetKeys))
    }

    @Test
    fun `FLEET user also unlocks elite-level features`() {
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.OSCILLOSCOPE_USB, fleetKeys))
        assertEquals(AccessDecision.Allowed, evaluateAccess(FeatureKey.REPORT_PDF_CERTIFIED, fleetKeys))
    }

    // ═══════════════════════════════════════════════════════════
    // Rewarded ad grants
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Rewarded grant unlocks feature temporarily`() {
        val grants = mapOf(FeatureKey.REPORT_PDF_BASIC to (System.currentTimeMillis() + 60_000))
        val decision = evaluateAccess(FeatureKey.REPORT_PDF_BASIC, emptySet(), rewardedGrants = grants)
        assertTrue(decision is AccessDecision.Allowed)
    }

    @Test
    fun `Expired rewarded grant does not unlock feature`() {
        val grants = mapOf(FeatureKey.REPORT_PDF_BASIC to (System.currentTimeMillis() - 1000))
        val decision = evaluateAccess(FeatureKey.REPORT_PDF_BASIC, emptySet(), rewardedGrants = grants)
        assertTrue(decision is AccessDecision.DeniedAdAvailable)
    }

    // ═══════════════════════════════════════════════════════════
    // Data model validation
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `Entitlement data class holds correct values`() {
        val e = Entitlement(
            id = "test-1",
            userId = "user-abc",
            entitlementKey = EntitlementKey.PRO_ACCESS,
            source = EntitlementSource.GOOGLE_PLAY_SUBSCRIPTION,
            state = EntitlementState.ACTIVE,
            expiresAt = null,
            purchaseTokenHash = "hash123",
            productId = "pro_monthly",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        assertEquals("test-1", e.id)
        assertEquals(EntitlementKey.PRO_ACCESS, e.entitlementKey)
        assertEquals(EntitlementState.ACTIVE, e.state)
        assertNull(e.expiresAt)
    }

    @Test
    fun `All FeatureKeys are covered in evaluateAccess`() {
        // This test ensures no when-branch throws — every FeatureKey is handled
        FeatureKey.entries.forEach { feature ->
            val result = evaluateAccess(feature, emptySet())
            assertNotNull("$feature should return a non-null decision", result)
        }
    }
}
