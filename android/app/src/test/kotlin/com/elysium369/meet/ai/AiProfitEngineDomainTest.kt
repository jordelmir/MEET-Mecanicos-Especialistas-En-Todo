package com.elysium369.meet.ai

import com.elysium369.meet.ai.domain.AiCommercialPlan
import com.elysium369.meet.ai.domain.AiQuotaSnapshot
import com.elysium369.meet.ai.domain.AiRoutingContext
import com.elysium369.meet.ai.domain.CostMicrosUsd
import com.elysium369.meet.ai.router.AiCostAwareRouter
import com.elysium369.meet.core.billing.PlayBillingCatalog
import com.elysium369.meet.core.monetization.EntitlementKey
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.ui.navigation.ProductUniverse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProfitEngineDomainTest {

    @Test
    fun `play billing catalog enforces fail-closed on unknown SKU`() {
        val known = PlayBillingCatalog.productOrNull(PlayBillingCatalog.PRO_MONTHLY)
        assertNotNull(known)
        assertEquals(EntitlementKey.PRO_ACCESS, known?.entitlementKey)

        val unknown = PlayBillingCatalog.productOrNull("malicious_unregistered_sku")
        assertNull(unknown)

        assertThrows(IllegalArgumentException::class.java) {
            PlayBillingCatalog.requireProduct("malicious_unregistered_sku")
        }
    }

    @Test
    fun `cost aware router routes deterministic and deep reasoning tasks`() {
        val router = AiCostAwareRouter()

        // 1. Budget exhausted
        val exhaustedCtx = AiRoutingContext(
            taskClass = AiTaskClass.DTC_SUMMARY.name,
            plan = AiCommercialPlan.PRO,
            caseCostMicrosUsd = 150_000L,
            caseBudgetMicrosUsd = 150_000L,
            complexity = "LOW",
            evidenceQuality = "HIGH"
        )
        val exhaustedDecision = router.route(exhaustedCtx)
        assertEquals("BUDGET_EXHAUSTED", exhaustedDecision.reason)
        assertEquals("local", exhaustedDecision.providerId)

        // 2. DTC Summary -> GPT-5.4 nano with low effort
        val dtcCtx = AiRoutingContext(
            taskClass = AiTaskClass.DTC_SUMMARY.name,
            plan = AiCommercialPlan.FREE,
            caseCostMicrosUsd = 10_000L,
            caseBudgetMicrosUsd = 40_000L,
            complexity = "LOW",
            evidenceQuality = "HIGH"
        )
        val dtcDecision = router.route(dtcCtx)
        assertEquals("openai", dtcDecision.providerId)
        assertEquals("gpt-5.4-nano", dtcDecision.modelId)
        assertEquals("low", dtcDecision.reasoningEffort)
        assertFalse(dtcDecision.allowEscalation)

        // 3. Deep repair reasoning for PRO with HIGH complexity escalates
        val proComplexCtx = AiRoutingContext(
            taskClass = AiTaskClass.DEEP_REPAIR_REASONING.name,
            plan = AiCommercialPlan.PRO,
            caseCostMicrosUsd = 20_000L,
            caseBudgetMicrosUsd = 150_000L,
            complexity = "HIGH",
            evidenceQuality = "EXACT"
        )
        val proComplexDecision = router.route(proComplexCtx)
        assertEquals("openai", proComplexDecision.providerId)
        assertEquals("gpt-5.4-mini", proComplexDecision.modelId)
        assertTrue(proComplexDecision.allowEscalation)

        // 4. Deep repair reasoning for FREE does NOT escalate
        val freeComplexCtx = AiRoutingContext(
            taskClass = AiTaskClass.DEEP_REPAIR_REASONING.name,
            plan = AiCommercialPlan.FREE,
            caseCostMicrosUsd = 5_000L,
            caseBudgetMicrosUsd = 40_000L,
            complexity = "HIGH",
            evidenceQuality = "EXACT"
        )
        val freeDecision = router.route(freeComplexCtx)
        assertEquals("gpt-5.4-nano", freeDecision.modelId)
        assertFalse(freeDecision.allowEscalation)
    }

    @Test
    fun `cost micros usd integer math prevents floating point errors`() {
        val costA = CostMicrosUsd(200_000L) // $0.20
        val costB = CostMicrosUsd(125_000L) // $0.125
        val total = costA + costB
        assertEquals(325_000L, total.value)
    }

    @Test
    fun `ai quota snapshot derives remaining cases and availability accurately`() {
        val snapshot = AiQuotaSnapshot(
            plan = AiCommercialPlan.PRO,
            periodKey = "2026-W36",
            includedCases = 5,
            consumedCases = 2,
            purchasedCases = 10
        )
        assertEquals(13, snapshot.remainingCases)
        assertTrue(snapshot.hasQuota)

        val exhaustedSnapshot = snapshot.copy(consumedCases = 15)
        assertEquals(0, exhaustedSnapshot.remainingCases)
        assertFalse(exhaustedSnapshot.hasQuota)
    }

    @Test
    fun `product topology resolves all primary universes with transversal ai`() {
        assertEquals(ProductUniverse.MY_VEHICLE, ProductUniverse.resolveUniverse(MeetDestinations.GARAGE))
        assertEquals(ProductUniverse.MY_VEHICLE, ProductUniverse.resolveUniverse(MeetDestinations.DNA))
        assertEquals(ProductUniverse.RESOLVE, ProductUniverse.resolveUniverse(MeetDestinations.MECHANIC_SERVICES))
        assertEquals(ProductUniverse.RESOLVE, ProductUniverse.resolveUniverse(MeetDestinations.TOW_TRUCK))
        assertEquals(ProductUniverse.DRIVE, ProductUniverse.resolveUniverse(MeetDestinations.HUD))
        assertEquals(ProductUniverse.DRIVE, ProductUniverse.resolveUniverse(MeetDestinations.TRIP_LOG))
        assertEquals(ProductUniverse.MOBILITY, ProductUniverse.resolveUniverse(MeetDestinations.RIDE_HOME))
        assertEquals(ProductUniverse.PROFESSIONAL, ProductUniverse.resolveUniverse(MeetDestinations.PRO_HUB))
        assertEquals(ProductUniverse.PROFESSIONAL, ProductUniverse.resolveUniverse(MeetDestinations.TERMINAL))
        assertEquals(ProductUniverse.ELYSIUM_AI, ProductUniverse.resolveUniverse(MeetDestinations.AI))
    }
}
