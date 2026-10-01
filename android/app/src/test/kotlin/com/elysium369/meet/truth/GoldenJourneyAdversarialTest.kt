package com.elysium369.meet.truth

import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementSnapshot
import com.elysium369.meet.core.agentstore.domain.EntitlementState
import com.elysium369.meet.core.operations.AutonomousOperationsEngine
import com.elysium369.meet.core.operations.CaseSeverity
import com.elysium369.meet.core.operations.CaseState
import com.elysium369.meet.core.finance.Money
import com.elysium369.meet.ride.application.RideApplicationService
import com.elysium369.meet.ride.application.RidePreviewRequest
import com.elysium369.meet.ride.application.PlaceResolutionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════
 *  GOLDEN JOURNEY ADVERSARIAL HARNESS (ASCENSION §A8)
 *  ──────────────────────────────────────────────────────────────
 *
 *  Five adversarial journeys that MUST pass on every build:
 *
 *  GJ1: Free-tier user attempts premium MasterMechanic → DENIED
 *  GJ2: Unknown destination ride request → NULL quote, not fake coords
 *  GJ3: Financial discrepancy → P0 case with REQUIRES_OWNER
 *  GJ4: EAOS DLQ detection → Truthful case, no fake remediation
 *  GJ5: Entitlements unavailable → Premium denied (fail-closed)
 * ══════════════════════════════════════════════════════════════════════
 */
class GoldenJourneyAdversarialTest {

    // ── GJ1: Free user cannot access premium capability ──

    @Test
    fun gj1_freeTierUserDeniedPremiumCapability() = runBlocking {
        val gateway = object : AgentEntitlementGateway {
            override fun currentPrincipalId(): String = "user_free"
            override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> {
                return Result.success(
                    EntitlementSnapshot(
                        principalId = "user_free",
                        entitlements = emptySet(),
                        asOfEpochMs = System.currentTimeMillis(),
                        revision = 1L
                    )
                )
            }
        }
        val repo = AgentEntitlementRepository(gateway)
        repo.refresh()

        // Free-tier user has NO entitlements
        assertFalse(
            "Free user MUST be denied agent.master_mechanic",
            repo.hasEntitlement("agent.master_mechanic"),
        )
        assertFalse(
            "Free user MUST be denied agent.evair_pro",
            repo.hasEntitlement("agent.evair_pro"),
        )
        // Null entitlement means free feature → allowed
        assertTrue(
            "Null entitlement (free feature) MUST be allowed",
            repo.hasEntitlement(null),
        )
    }

    // ── GJ2: Unknown destination produces null quote, not fake coords ──

    @Test
    fun gj2_unknownDestinationReturnsNullNotFakeCoords() {
        val service = RideApplicationService(
            commandBus = FakeCommandBus(),
            savedPlacesStore = null,
        )

        // Try to resolve a completely unknown place
        val result = service.resolveGeocodingQuery("Planeta Marte Base Alpha")
        assertTrue(
            "Unknown destination MUST return NotFound, got: $result",
            result is PlaceResolutionResult.NotFound,
        )

        // Attempt to generate a quote with unknown destination
        val quote = service.generatePreviewQuote(
            principalId = "user_test",
            request = RidePreviewRequest(
                principalId = "user_test",
                destinationQuery = "Planeta Marte Base Alpha",
            ),
        )
        assertNull(
            "Quote for unknown destination MUST be null — never fabricate coordinates",
            quote,
        )
    }

    // ── GJ3: Financial discrepancy creates P0 owner-required case ──

    @Test
    fun gj3_financialDiscrepancyCreatesP0OwnerCase() {
        val engine = AutonomousOperationsEngine()
        val result = engine.evaluateFinancialIntegrity(
            mismatchedTransactionsCount = 3,
            unbalancedMoneyExposure = Money(minorUnits = 25000L, currency = "CRC"),
        )

        assertNotNull("Financial discrepancy MUST create a case", result)
        assertEquals("Domain MUST be FINANCE", "FINANCE", result!!.domain)
        assertEquals("Severity MUST be P0", CaseSeverity.P0, result.severity)
        assertEquals("State MUST be REQUIRES_OWNER", CaseState.REQUIRES_OWNER, result.state)
        assertTrue(
            "Case MUST require human attention",
            result.requiresHumanAttention,
        )
        assertFalse(
            "Case MUST NOT contain fake 'conciliado' claim",
            result.whatAutomationDid.contains("100% conciliado"),
        )
    }

    // ── GJ4: DLQ detection produces truthful case ──

    @Test
    fun gj4_dlqDetectionProducesTruthfulCase() {
        val engine = AutonomousOperationsEngine()
        val result = engine.evaluateSreHealth(
            outboxLagSeconds = 10L,
            deadLetterCount = 5,
            apiAvailabilityPercent = 99.5,
        )

        assertNotNull("DLQ > 0 MUST create an SRE case", result)
        assertEquals("Domain MUST be SRE", "SRE", result!!.domain)
        assertEquals("Severity MUST be P0 for DLQ", CaseSeverity.P0, result.severity)
        assertTrue(
            "Evidence summary MUST mention actual count",
            result.evidenceSummary.contains("5"),
        )
        assertFalse(
            "MUST NOT claim auto-scaled concurrency",
            result.whatAutomationDid.contains("escaló la concurrencia"),
        )
    }

    // ── GJ5: Backend unavailable → premium denied (fail-closed) ──

    @Test
    fun gj5_backendUnavailableDeniesAllPremium() = runBlocking {
        val gateway = object : AgentEntitlementGateway {
            override fun currentPrincipalId(): String = "user_free"
            override suspend fun fetchAuthoritativeEntitlements(): Result<EntitlementSnapshot> {
                return Result.failure(RuntimeException("Server unreachable"))
            }
        }
        val repo = AgentEntitlementRepository(gateway)
        repo.refresh()

        // State must be Unavailable
        assertTrue(
            "State MUST be Unavailable when backend fails",
            repo.state.value is EntitlementState.Unavailable,
        )
        // Premium MUST be denied
        assertFalse(
            "Premium MUST be denied when backend is unavailable",
            repo.hasEntitlement("agent.master_mechanic"),
        )
        assertFalse(
            "Any premium entitlement MUST be denied when backend is unavailable",
            repo.hasEntitlement("agent.evair_pro"),
        )
        // Free features still work
        assertTrue(
            "Free features (null entitlement) MUST still work",
            repo.hasEntitlement(null),
        )
    }

    // ── Test doubles ──

    private class FakeCommandBus : com.elysium369.meet.ride.application.RideCommandBus {
        override suspend fun enqueue(command: com.elysium369.meet.ride.data.remote.RideQueuedCommand): com.elysium369.meet.ride.application.RideCommandEnqueueResult {
            return com.elysium369.meet.ride.application.RideCommandEnqueueResult.Enqueued
        }
    }
}
