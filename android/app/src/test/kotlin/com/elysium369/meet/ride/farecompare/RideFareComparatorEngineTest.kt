package com.elysium369.meet.ride.farecompare

import com.elysium369.meet.ride.domain.RideFareMode
import org.junit.Assert.*
import org.junit.Test

class RideFareComparatorEngineTest {

    @Test
    fun `compare returns both metered and bid estimates`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 15.0, durationMinutes = 20,
            baseFare = 800, distanceRate = 200, timeRate = 50,
        )
        assertTrue(result.meteredEstimate.estimatedAmount > 0)
        assertTrue(result.openBidEstimate.estimatedAmount > 0)
        assertNotNull(result.openBidEstimate.formattedRange)
    }

    @Test
    fun `metered fare breakdown is correct`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 10.0, durationMinutes = 15,
            baseFare = 800, distanceRate = 200, timeRate = 50,
            activeDrivers = 10, activeRequests = 5, // Normal demand
        )
        val breakdown = result.meteredEstimate.breakdown!!
        assertEquals(800L, breakdown.baseFare)
        assertEquals(2000L, breakdown.distanceFare) // 10 * 200
        assertEquals(750L, breakdown.timeFare)       // 15 * 50
    }

    @Test
    fun `high demand adds surge to metered`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 10.0, durationMinutes = 15,
            baseFare = 800, distanceRate = 200, timeRate = 50,
            activeDrivers = 3, activeRequests = 10, // Very high demand
        )
        assertTrue(result.meteredEstimate.hasSurcharge)
        assertTrue(result.meteredEstimate.surchargePercent > 0)
        assertTrue(result.demand.level in listOf(DemandLevel.VERY_HIGH, DemandLevel.EXTREME))
    }

    @Test
    fun `low demand suggests negotiation`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 10.0, durationMinutes = 15,
            baseFare = 800, distanceRate = 200, timeRate = 50,
            activeDrivers = 20, activeRequests = 5, // Low demand
        )
        assertEquals(DemandLevel.LOW, result.demand.level)
        assertEquals(0.0, result.demand.surchargePercent, 0.01)
    }

    @Test
    fun `route history improves estimates`() {
        val e = RideFareComparatorEngine()
        // Record 5+ fares to become reliable
        repeat(6) {
            e.recordFare(9.93, -84.08, 10.0, -84.21, 4000L + it * 100, 20, 15.0)
        }
        val stats = e.getRouteStats(9.93, -84.08, 10.0, -84.21)!!
        assertTrue(stats.isReliable)
        assertTrue(stats.sampleCount >= 5)
        assertTrue(stats.averageFare > 0)
    }

    @Test
    fun `unreliable route stats with few samples`() {
        val e = RideFareComparatorEngine()
        e.recordFare(9.93, -84.08, 10.0, -84.21, 4000, 20, 15.0)
        e.recordFare(9.93, -84.08, 10.0, -84.21, 4500, 22, 15.0)
        val stats = e.getRouteStats(9.93, -84.08, 10.0, -84.21)!!
        assertFalse(stats.isReliable) // < 5 samples
    }

    @Test
    fun `recommendation always provided with reason`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 10.0, durationMinutes = 15,
            baseFare = 800, distanceRate = 200, timeRate = 50,
        )
        assertNotNull(result.recommendation)
        assertTrue(result.recommendation.confidence > 0.0)
        assertTrue(result.recommendation.message.isNotBlank())
    }

    @Test
    fun `short trip recommends metered`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 3.0, durationMinutes = 8,
            baseFare = 800, distanceRate = 200, timeRate = 50,
            activeDrivers = 10, activeRequests = 8,
        )
        assertEquals(RideFareMode.METERED_TIME_DISTANCE, result.recommendation.recommendedMode)
        assertEquals(RecommendationReason.SHORT_TRIP_METERED, result.recommendation.reason)
    }

    @Test
    fun `comparison summary contains all key info`() {
        val e = RideFareComparatorEngine()
        val result = e.compare(
            distanceKm = 15.0, durationMinutes = 25,
            baseFare = 800, distanceRate = 200, timeRate = 50,
        )
        val summary = result.comparisonSummary
        assertTrue(summary.contains("Taxímetro"))
        assertTrue(summary.contains("Oferta libre"))
        assertTrue(summary.contains("Demanda"))
        assertTrue(summary.contains("💡"))
    }

    @Test
    fun `surge capped at maximum`() {
        val e = RideFareComparatorEngine()
        // Extreme demand
        val demand = e.computeDemand(activeDrivers = 1, activeRequests = 100)
        assertTrue(demand.surchargePercent <= RideFareComparatorEngine.MAX_SURGE_PERCENT)
    }

    @Test
    fun `demand levels have Spanish labels`() {
        assertEquals("Baja demanda", DemandLevel.LOW.displayLabel)
        assertEquals("Demanda extrema", DemandLevel.EXTREME.displayLabel)
    }

    @Test
    fun `demand levels have distinct emojis`() {
        val emojis = DemandLevel.entries.map { it.emoji }.toSet()
        assertEquals(5, emojis.size) // All unique
    }
}
