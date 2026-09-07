package com.elysium369.meet.ride.earnings

import org.junit.Assert.*
import org.junit.Test

class RideDriverEarningsEngineTest {

    private fun engineWithTrips(): RideDriverEarningsEngine {
        val e = RideDriverEarningsEngine()
        e.recordTrip("r1", "d1", 5000, tipAmount = 500, distanceKm = 10.0,
            durationMinutes = 20, pickupName = "Centro", dropoffName = "Aeropuerto",
            passengerRating = 5)
        e.recordTrip("r2", "d1", 3000, distanceKm = 6.0,
            durationMinutes = 12, pickupName = "Escazú", dropoffName = "Centro",
            passengerRating = 4)
        return e
    }

    @Test
    fun `record trip with 5 percent platform fee`() {
        val e = RideDriverEarningsEngine()
        val trip = e.recordTrip("r1", "d1", 10000, distanceKm = 15.0,
            durationMinutes = 25, pickupName = "A", dropoffName = "B")
        assertEquals(500L, trip.platformFee) // 10000 * 5%
        assertEquals(9500L, trip.netEarning) // 10000 - 500
        assertTrue(trip.platformFeePercent <= 5.0)
    }

    @Test
    fun `platform fee never exceeds 5 percent constitutional max`() {
        val e = RideDriverEarningsEngine()
        val trip = e.recordTrip("r1", "d1", 10000, distanceKm = 10.0,
            durationMinutes = 15, pickupName = "A", dropoffName = "B",
            platformFeePercent = 15.0) // Try 15% → capped to 5%
        assertTrue(trip.platformFeePercent <= 5.0)
        assertEquals(500L, trip.platformFee) // Still 5%
    }

    @Test
    fun `tips go entirely to driver`() {
        val e = RideDriverEarningsEngine()
        val trip = e.recordTrip("r1", "d1", 5000, tipAmount = 2000,
            distanceKm = 8.0, durationMinutes = 15, pickupName = "A", dropoffName = "B")
        // platformFee = 5000 * 5% = 250 (NOT on tip)
        // net = 5000 + 2000 - 250 = 6750
        assertEquals(250L, trip.platformFee)
        assertEquals(6750L, trip.netEarning)
    }

    @Test
    fun `earnings summary computes correctly`() {
        val e = engineWithTrips()
        val summary = e.computeSummary("d1", "Hoy")
        assertEquals(2, summary.totalTrips)
        assertEquals(500L, summary.totalTips)
        assertTrue(summary.totalNet > 0)
        assertTrue(summary.averageRating > 0)
        assertEquals("Hoy", summary.periodLabel)
    }

    @Test
    fun `expense tracking by category`() {
        val e = engineWithTrips()
        e.addExpense("d1", ExpenseCategory.FUEL, 15000, "Tanque lleno")
        e.addExpense("d1", ExpenseCategory.CAR_WASH, 3000, "Lavado exterior")
        e.addExpense("d1", ExpenseCategory.FUEL, 8000, "Medio tanque")
        val byCategory = e.getExpensesByCategory("d1")
        assertEquals(23000L, byCategory[ExpenseCategory.FUEL])
        assertEquals(3000L, byCategory[ExpenseCategory.CAR_WASH])
    }

    @Test
    fun `net income subtracts expenses`() {
        val e = engineWithTrips()
        val earningsOnly = e.getNetIncome("d1")
        e.addExpense("d1", ExpenseCategory.FUEL, 5000)
        val afterExpenses = e.getNetIncome("d1")
        assertEquals(earningsOnly - 5000, afterExpenses)
    }

    @Test
    fun `income goal tracks progress`() {
        val e = RideDriverEarningsEngine()
        val goal = e.setGoal("d1", 50000, GoalPeriod.DAILY)
        assertFalse(goal.isAchieved)
        assertEquals(0.0, goal.progressPercent, 0.01)
        // Record trips that contribute to goal
        e.recordTrip("r1", "d1", 30000, distanceKm = 20.0,
            durationMinutes = 30, pickupName = "A", dropoffName = "B")
        val updated = e.getActiveGoals("d1").first()
        assertTrue(updated.progressPercent > 0)
    }

    @Test
    fun `peak hours returns 24 entries sorted by earnings`() {
        val e = engineWithTrips()
        val peaks = e.getPeakHours("d1")
        assertEquals(24, peaks.size)
        // First entry should have highest average earning
        assertTrue(peaks.first().averageEarning >= peaks.last().averageEarning)
    }

    @Test
    fun `performance score with perfect metrics`() {
        val e = engineWithTrips()
        val perf = e.computePerformance("d1",
            totalOffered = 100, totalAccepted = 98,
            totalCompleted = 97, totalOnTime = 95)
        assertTrue(perf.overallScore > 80)
        assertTrue(perf.tier in listOf(PerformanceTier.GOLD, PerformanceTier.PLATINUM, PerformanceTier.DIAMOND))
    }

    @Test
    fun `performance tiers map correctly`() {
        val e = RideDriverEarningsEngine()
        // Low acceptance → lower score
        val lowPerf = e.computePerformance("d2",
            totalOffered = 100, totalAccepted = 30,
            totalCompleted = 25, totalOnTime = 10)
        assertEquals(PerformanceTier.BRONZE, lowPerf.tier)
    }

    @Test
    fun `recent trips returns latest first`() {
        val e = engineWithTrips()
        val recent = e.getRecentTrips("d1")
        assertEquals(2, recent.size)
        assertTrue(recent.first().timestampMs >= recent.last().timestampMs)
    }

    @Test
    fun `expense labels are in Spanish`() {
        assertEquals("Combustible", ExpenseCategory.FUEL.displayLabel)
        assertEquals("Mantenimiento", ExpenseCategory.MAINTENANCE.displayLabel)
        assertEquals("Peaje", ExpenseCategory.TOLL.displayLabel)
    }
}
