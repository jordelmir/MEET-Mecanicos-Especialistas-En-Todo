package com.elysium369.meet.core.fleet

import org.junit.Assert.*
import org.junit.Test

class FleetCommandEngineTest {
    @Test fun `add vehicles to fleet`() {
        val e = FleetCommandEngine()
        val v = e.addVehicle("ABC-123", "Toyota", "Hilux", 2022, currentMileageKm = 45000)
        assertEquals("Toyota Hilux 2022 (ABC-123)", v.displayName)
        assertEquals(1, e.totalVehicles)
    }
    @Test fun `assign driver`() {
        val e = FleetCommandEngine()
        val v = e.addVehicle("ABC-123", "Toyota", "Hilux", 2022)
        assertTrue(e.assignDriver(v.vehicleId, "d1", "Carlos"))
        assertEquals("Carlos", e.getVehicle(v.vehicleId)!!.assignedDriverName)
    }
    @Test fun `cost tracking by category`() {
        val e = FleetCommandEngine()
        val v = e.addVehicle("ABC-123", "Toyota", "Hilux", 2022)
        e.recordCost(v.vehicleId, FleetCostCategory.FUEL, 25000)
        e.recordCost(v.vehicleId, FleetCostCategory.FUEL, 30000)
        e.recordCost(v.vehicleId, FleetCostCategory.REPAIR, 80000)
        val byCategory = e.getCostsByCategory()
        assertEquals(55000L, byCategory[FleetCostCategory.FUEL])
        assertEquals(80000L, byCategory[FleetCostCategory.REPAIR])
    }
    @Test fun `alerts for low health score`() {
        val e = FleetCommandEngine()
        val v = e.addVehicle("ABC-123", "Toyota", "Hilux", 2022)
        e.updateHealthScore(v.vehicleId, 350)
        val alerts = e.generateAlerts()
        assertTrue(alerts.any { it.type == FleetAlertType.LOW_HEALTH_SCORE && it.severity == AlertSeverity.CRITICAL })
    }
    @Test fun `fleet summary`() {
        val e = FleetCommandEngine()
        e.addVehicle("A", "Toyota", "Hilux", 2022)
        e.addVehicle("B", "Hyundai", "Tucson", 2021)
        val v3 = e.addVehicle("C", "Nissan", "Frontier", 2020)
        e.updateStatus(v3.vehicleId, FleetVehicleStatus.MAINTENANCE)
        val summary = e.getSummary()
        assertEquals(3, summary.totalVehicles)
        assertEquals(2, summary.activeVehicles)
        assertEquals(1, summary.inMaintenance)
    }
    @Test fun `vehicles sorted by health`() {
        val e = FleetCommandEngine()
        val v1 = e.addVehicle("A", "Toyota", "A", 2022)
        val v2 = e.addVehicle("B", "Honda", "B", 2021)
        e.updateHealthScore(v1.vehicleId, 900)
        e.updateHealthScore(v2.vehicleId, 400)
        val sorted = e.getVehiclesByHealth()
        assertTrue(sorted.first().healthScore <= sorted.last().healthScore)
    }
}
