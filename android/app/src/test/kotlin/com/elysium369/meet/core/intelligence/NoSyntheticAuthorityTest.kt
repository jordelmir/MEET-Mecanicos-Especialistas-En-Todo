package com.elysium369.meet.core.intelligence

import com.elysium369.meet.core.finance.Money
import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.intelligence.engine.AnomalyLevel
import com.elysium369.meet.core.intelligence.engine.IntelligenceEngine
import com.elysium369.meet.core.owner.domain.DataFreshness
import com.elysium369.meet.core.owner.domain.OwnerCommandCenterSnapshot
import com.elysium369.meet.core.owner.domain.OwnerMobilitySnapshot
import com.elysium369.meet.core.owner.domain.OwnerMoneySnapshot
import com.elysium369.meet.core.owner.domain.OwnerSystemHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate test enforced by ELYSIUM MASTER ORDER OMEGA §45, §96:
 * ZERO synthetic business truth in production dashboards.
 * Must fail if any fixture numbers (such as 875_422_190L) are returned as authoritative truth.
 */
class NoSyntheticAuthorityTest {

    private val engine = IntelligenceEngine()
    private val executiveScope = AnalyticsScope(
        principalId = "test-platform-owner",
        scopeType = ScopeType.EXECUTIVE,
        capabilities = setOf(ActorCapability.VIEW_PLATFORM_INTELLIGENCE),
    )

    @Test
    fun projectExecutive_withoutAuthoritativeSnapshot_returnsHonestUnavailableState() {
        val projection = engine.projectExecutive(executiveScope, authoritativeSnapshot = null)

        assertEquals("Freshness must be UNAVAILABLE when disconnected from backend", DataFreshness.UNAVAILABLE, projection.freshness)
        assertEquals("Total GMV must be 0, never synthetic billions", 0L, projection.totalGmv.minorUnits)
        assertEquals("Platform revenue must be 0", 0L, projection.platformRevenue.minorUnits)
        assertEquals("Active users must be 0", 0, projection.activeUsers)
        assertEquals("Active providers must be 0", 0, projection.activeProviders)
        assertEquals("Trips must be 0", 0, projection.completedTrips)
        assertTrue("Anomalies must be empty when no authoritative data exists", projection.anomalies.isEmpty())

        // Explicitly assert that the old synthetic 875M CRC is forbidden
        assertNotEquals(875_422_190L, projection.totalGmv.minorUnits)
    }

    @Test
    fun projectExecutive_withAuthoritativeSnapshot_derivesMetricsStrictlyFromSnapshot() {
        val snapshot = OwnerCommandCenterSnapshot(
            freshness = DataFreshness.LIVE,
            money = OwnerMoneySnapshot(
                totalGmv = Money.ofCrc(150_000L),
                platformRevenue = Money.ofCrc(22_500L),
                netRevenue = Money.ofCrc(20_000L),
            ),
            mobility = OwnerMobilitySnapshot(
                activeUsers = 120,
                activeProviders = 15,
                completedTrips = 85,
            ),
            systemHealth = OwnerSystemHealthSnapshot(
                apiAvailabilityPercent = 99.98,
                p95LatencyMs = 120L,
                outboxLagSeconds = 120L, // Should trigger a warning anomaly
                deadLetterCount = 2,    // Should trigger a critical anomaly
                workersHealthy = true,
            ),
            servicesCompleted = 10,
            towCallsCompleted = 2,
        )

        val projection = engine.projectExecutive(executiveScope, authoritativeSnapshot = snapshot)

        assertEquals(DataFreshness.LIVE, projection.freshness)
        assertEquals(150_000L, projection.totalGmv.minorUnits)
        assertEquals(22_500L, projection.platformRevenue.minorUnits)
        assertEquals(120, projection.activeUsers)
        assertEquals(15, projection.activeProviders)
        assertEquals(85, projection.completedTrips)
        assertEquals(10, projection.completedServices)
        assertEquals(2, projection.completedTowCalls)

        // Verifies real anomaly detection derived from health metrics
        assertTrue("Must detect DLQ critical anomaly", projection.anomalies.any { it.level == AnomalyLevel.CRITICAL && it.domain == "System" })
        assertTrue("Must detect Outbox lag anomaly", projection.anomalies.any { it.level == AnomalyLevel.WARNING && it.domain == "Outbox" })
    }
}
