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

    @Test
    fun projectAllProjections_withoutTelemetry_returnHonestZeroesNeverSyntheticFixtures() {
        val driverScope = AnalyticsScope(principalId = "driver-1", scopeType = ScopeType.PERSONAL, capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.DRIVE_RIDE))
        val driverProj = engine.projectDriver(driverScope, "driver-1")
        assertEquals(0L, driverProj.todayGrossEarnings.minorUnits)
        assertEquals(0L, driverProj.todayNetEarnings.minorUnits)
        assertEquals(0, driverProj.todayTripsCount)
        assertEquals(0.0, driverProj.totalKm, 0.001)

        val fleetScope = AnalyticsScope(principalId = "fleet-mgr", organizationId = "fleet-1", scopeType = ScopeType.FLEET, capabilities = setOf(ActorCapability.VIEW_ORGANIZATION_ANALYTICS))
        val fleetProj = engine.projectFleet(fleetScope, "fleet-1")
        assertEquals(0, fleetProj.totalVehicles)
        assertEquals(0, fleetProj.activeVehicles)
        assertEquals(0, fleetProj.tripsToday)
        assertEquals(0L, fleetProj.fleetGmv.minorUnits)

        val passengerScope = AnalyticsScope(principalId = "pax-1", scopeType = ScopeType.PASSENGER, capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS))
        val paxProj = engine.projectPassenger(passengerScope, "pax-1")
        assertEquals(0, paxProj.completedTripsCount)
        assertEquals(0.0, paxProj.totalDistanceKm, 0.001)
        assertEquals(0L, paxProj.totalSpent.minorUnits)

        val mechScope = AnalyticsScope(principalId = "mech-1", scopeType = ScopeType.PERSONAL, capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.PROVIDE_REPAIR))
        val mechProj = engine.projectMechanic(mechScope, "mech-1")
        assertEquals(0, mechProj.jobsCompletedCount)
        assertEquals(0L, mechProj.totalRevenue.minorUnits)

        val wsScope = AnalyticsScope(principalId = "ws-owner", organizationId = "ws-1", scopeType = ScopeType.WORKSHOP, capabilities = setOf(ActorCapability.VIEW_ORGANIZATION_ANALYTICS))
        val wsProj = engine.projectWorkshop(wsScope, "ws-1")
        assertEquals(0, wsProj.totalBays)
        assertEquals(0, wsProj.activeJobsCount)
        assertEquals(0L, wsProj.todayRevenue.minorUnits)

        val towScope = AnalyticsScope(principalId = "tow-1", scopeType = ScopeType.PERSONAL, capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.PROVIDE_TOW))
        val towProj = engine.projectTow(towScope, "tow-1")
        assertEquals(0, towProj.availableTrucksCount)
        assertEquals(0, towProj.completedCallsCount)
        assertEquals(0L, towProj.totalGmv.minorUnits)
    }
}
