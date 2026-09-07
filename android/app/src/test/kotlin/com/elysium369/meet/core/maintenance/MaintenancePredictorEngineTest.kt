package com.elysium369.meet.core.maintenance

import org.junit.Assert.*
import org.junit.Test

class MaintenancePredictorEngineTest {
    @Test fun `new vehicle mostly green`() {
        val e = MaintenancePredictorEngine()
        // Record all services at km 0 so nothing is overdue at 1000km
        VehicleComponent.entries.forEach { e.recordService("v1", it, atKm = 0) }
        val plan = e.predict("v1", currentKm = 1000)
        assertEquals(0, plan.overdueCount)
    }
    @Test fun `high mileage shows overdue items`() {
        val e = MaintenancePredictorEngine()
        val plan = e.predict("v1", currentKm = 100000) // No service records
        assertTrue(plan.overdueCount > 0)
        assertEquals(PredictionUrgency.OVERDUE, plan.overallUrgency)
    }
    @Test fun `service record extends life`() {
        val e = MaintenancePredictorEngine()
        e.recordService("v1", VehicleComponent.ENGINE_OIL, atKm = 95000)
        val plan = e.predict("v1", currentKm = 100000)
        val oil = plan.predictions.find { it.component == VehicleComponent.ENGINE_OIL }!!
        assertTrue(oil.remainingKm > 0) // Should have ~2500 km left
        assertTrue(oil.urgency != PredictionUrgency.OVERDUE)
    }
    @Test fun `savings calculated correctly`() {
        val e = MaintenancePredictorEngine()
        val plan = e.predict("v1", currentKm = 100000)
        val timing = plan.predictions.find { it.component == VehicleComponent.TIMING_BELT }
        assertNotNull(timing)
        assertTrue(timing!!.savingsIfPreventive > 0)
        assertEquals(timing.emergencyCost - timing.preventiveCost, timing.savingsIfPreventive)
    }
    @Test fun `urgency labels have emojis`() {
        assertTrue(PredictionUrgency.GREEN.displayLabel.contains("🟢"))
        assertTrue(PredictionUrgency.RED.displayLabel.contains("🔴"))
        assertTrue(PredictionUrgency.OVERDUE.displayLabel.contains("⚫"))
    }
    @Test fun `component labels in Spanish`() {
        assertEquals("Aceite de motor", VehicleComponent.ENGINE_OIL.displayLabel)
        assertEquals("Banda de tiempo", VehicleComponent.TIMING_BELT.displayLabel)
        assertEquals("Pastillas de freno (del.)", VehicleComponent.BRAKE_PADS_FRONT.displayLabel)
    }
    @Test fun `overdue items query`() {
        val e = MaintenancePredictorEngine()
        val overdue = e.getOverdueItems("v1", currentKm = 150000)
        assertTrue(overdue.isNotEmpty())
        assertTrue(overdue.all { it.urgency == PredictionUrgency.OVERDUE })
    }
}
