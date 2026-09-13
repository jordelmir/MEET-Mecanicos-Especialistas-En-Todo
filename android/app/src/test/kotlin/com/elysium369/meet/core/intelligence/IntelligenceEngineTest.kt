package com.elysium369.meet.core.intelligence

import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.intelligence.engine.IntelligenceEngine
import com.elysium369.meet.core.intelligence.metrics.MetricsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceEngineTest {

    private val engine = IntelligenceEngine()

    @Test
    fun passengerActivityProjection_CalculatesAveragesAndCo2() {
        val scope = AnalyticsScope(
            principalId = "passenger-123",
            scopeType = ScopeType.PASSENGER,
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS),
        )

        val activity = engine.projectPassenger(
            scope = scope,
            passengerId = "passenger-123",
            tripsCount = 20,
            totalKm = 200.0,
            totalHours = 10.0,
            spentUnits = 60_000L,
        )

        assertEquals("Average per trip should be ₡3,000", 3_000L, activity.averagePerTrip.minorUnits)
        assertEquals("Average per km should be ₡300", 300L, activity.averagePerKm.minorUnits)
        assertEquals("Estimated CO2 should be 24.0 kg", 24.0, activity.estimatedCo2Kg, 0.01)
    }

    @Test
    fun mechanicBusinessProjection_CalculatesReworkAndTicket() {
        val scope = AnalyticsScope(
            principalId = "mechanic-456",
            scopeType = ScopeType.PERSONAL,
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.PROVIDE_REPAIR),
        )

        val biz = engine.projectMechanic(
            scope = scope,
            mechanicId = "mechanic-456",
            jobsCompleted = 50,
            laborUnits = 1_000_000L,
            partsUnits = 500_000L,
            billedHours = 100.0,
            reworkJobs = 1,
        )

        assertEquals("Total revenue should be ₡1.5M", 1_500_000L, biz.totalRevenue.minorUnits)
        assertEquals("Average ticket should be ₡30,000", 30_000L, biz.averageTicket.minorUnits)
        assertEquals("Revenue per hour should be ₡10,000/h", 10_000L, biz.revenuePerHour.minorUnits)
        assertEquals("Rework 30d rate should be 2.0%", 2.0, biz.rework30dRatePercent, 0.01)
    }

    @Test
    fun workshopCommandCenter_EnforcesTenantIsolation() {
        val workshopScopeA = AnalyticsScope(
            principalId = "admin-workshop-a",
            organizationId = "workshop-org-a",
            scopeType = ScopeType.WORKSHOP,
            capabilities = setOf(ActorCapability.VIEW_ORGANIZATION_ANALYTICS),
        )

        // Authorized query succeeds
        val workshopA = engine.projectWorkshop(
            scope = workshopScopeA,
            workshopOrgId = "workshop-org-a",
            totalBays = 10,
            occupiedBays = 7,
        )
        assertEquals(70.0, workshopA.bayUtilizationPercent, 0.01)

        // Cross-tenant attempt must fail
        assertFalse(workshopScopeA.canAccess(targetOrgId = "workshop-org-b", targetSubjectId = null))
        var threw = false
        try {
            engine.projectWorkshop(scope = workshopScopeA, workshopOrgId = "workshop-org-b")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Querying other workshop org must throw IllegalArgumentException", threw)
    }

    @Test
    fun towCommandCenter_CalculatesLoadedRatio() {
        val scope = AnalyticsScope(
            principalId = "tow-operator-789",
            scopeType = ScopeType.PERSONAL,
            capabilities = setOf(ActorCapability.VIEW_PERSONAL_ANALYTICS, ActorCapability.PROVIDE_TOW),
        )

        val tow = engine.projectTow(
            scope = scope,
            operatorOrOrgId = "tow-operator-789",
            isOrganization = false,
            totalKm = 500.0,
            loadedKm = 350.0,
        )

        assertEquals("Loaded ratio should be 0.70 (70%)", 0.70, tow.loadedRatio, 0.01)
    }

    @Test
    fun metricsRegistry_EdgeCases() {
        assertEquals("Utilization on 0 hours should be 0", 0.0, MetricsRegistry.calculateUtilization(0.0, 0.0), 0.001)
        assertEquals("Deadhead on 0 km should be 0", 0.0, MetricsRegistry.calculateDeadheadRatio(0.0, 0.0), 0.001)
        assertEquals("Loaded ratio on 0 km should be 0", 0.0, MetricsRegistry.calculateLoadedRatio(0.0, 0.0), 0.001)
        assertEquals("Rework rate on 0 jobs should be 0", 0.0, MetricsRegistry.calculateReworkRate(0, 0), 0.001)
    }
}
