package com.elysium369.meet.core.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleHealthScoreEngineTest {
    @Test fun `perfect vehicle scores excellent`() {
        val e = VehicleHealthScoreEngine()
        val entry = e.computeScore("v1", HealthScoreFactors())
        assertEquals(HealthTier.EXCELLENT, entry.tier)
        assertEquals(1000, entry.score)
    }
    @Test fun `active DTCs reduce score`() {
        val e = VehicleHealthScoreEngine()
        val entry = e.computeScore("v1", HealthScoreFactors(activeDtcCount = 3, criticalDtcCount = 1))
        assertTrue(entry.score < 900)
        assertTrue(entry.breakdown.dtcScore < 300)
    }
    @Test fun `old high-mileage vehicle penalized`() {
        val e = VehicleHealthScoreEngine()
        val entry = e.computeScore("v1", HealthScoreFactors(vehicleAgeYears = 22, totalMileageKm = 350000))
        assertTrue(entry.score < 900)
        assertTrue(entry.breakdown.ageMileageScore < 200)
    }
    @Test fun `flood damage severely impacts score`() {
        val e = VehicleHealthScoreEngine()
        val entry = e.computeScore("v1", HealthScoreFactors(floodDamage = true, accidentCount = 2))
        assertTrue(entry.breakdown.incidentScore < 50)
    }
    @Test fun `score trend tracks history`() {
        val e = VehicleHealthScoreEngine()
        e.computeScore("v1", HealthScoreFactors())
        e.computeScore("v1", HealthScoreFactors(activeDtcCount = 2))
        val trend = e.getScoreTrend("v1")
        assertEquals(2, trend.size)
        val delta = e.getScoreDelta("v1")
        assertNotNull(delta)
        assertTrue(delta!! < 0) // Score went down
    }
    @Test fun `issues identified correctly`() {
        val e = VehicleHealthScoreEngine()
        val entry = e.computeScore("v1", HealthScoreFactors(
            activeDtcCount = 2, missedMaintenanceCount = 1, accidentCount = 1))
        assertTrue(entry.breakdown.topIssues.any { it.contains("DTC") })
        assertTrue(entry.breakdown.topIssues.any { it.contains("accidente") })
    }
    @Test fun `tier labels and emojis exist`() {
        assertEquals("🟢", HealthTier.EXCELLENT.emoji)
        assertEquals("🔴", HealthTier.CRITICAL.emoji)
        assertTrue(HealthTier.EXCELLENT.displayLabel.contains("Excelente"))
    }
    @Test fun `share text contains score`() {
        val e = VehicleHealthScoreEngine()
        e.computeScore("v1", HealthScoreFactors())
        val text = e.generateShareText("v1")
        assertTrue(text.contains("1000"))
        assertTrue(text.contains("ELYSIUM"))
    }
}
