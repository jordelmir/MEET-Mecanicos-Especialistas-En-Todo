package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetPeritoTest {

    @Test
    fun testInspectionWithValidVin() {
        val perito = MeetPerito()

        // Valid Ford Escape 2012 VIN
        val validVin = "1FMCU0GZ2CUA00001"

        val report = perito.performInspection(
            context = null, // Safely nullable in tests
            vehicleId = "veh_test_123",
            vin = validVin,
            activeDtcs = emptyList(),
            pendingDtcs = emptyList(),
            freezeFrame = null,
            liveData = mapOf(
                "0107" to 0.0f, // LTFT
                "0105" to 90.0f, // Coolant
                "0142" to 13.8f  // Voltage
            ),
            odometerKmCluster = 120000L,
            readinessMonitors = mapOf("MISFIRE" to true, "FUEL" to true),
            dtcScanComplete = true,
            freezeFrameReadComplete = true,
        )

        // It should pass with excellent score because VIN is valid and there are no faults
        assertTrue(report.score0to100 >= 95)
        assertEquals("Excelente", report.category)
        assertTrue(report.criticalIssues.isEmpty())
        assertTrue("formato canónico" in (report.dimensionsDetails["VIN"] ?: ""))
    }

    @Test
    fun testInspectionWithTamperedVinTriggersRedFlag() {
        val perito = MeetPerito()

        // Tampered VIN (check digit replaced by '9')
        val tamperedVin = "1FMCU0GZ9CUA00001"

        val report = perito.performInspection(
            context = null, // Safely nullable in tests
            vehicleId = "veh_test_123",
            vin = tamperedVin,
            activeDtcs = emptyList(),
            pendingDtcs = emptyList(),
            freezeFrame = null,
            liveData = mapOf(
                "0107" to 0.0f, // LTFT
                "0105" to 90.0f, // Coolant
                "0142" to 13.8f  // Voltage
            ),
            odometerKmCluster = 120000L,
            readinessMonitors = mapOf("MISFIRE" to true, "FUEL" to true),
            dtcScanComplete = true,
            freezeFrameReadComplete = true,
        )

        // Should have a critical issue for the bad VIN and drop the score significantly
        assertTrue(report.score0to100 <= 80)
        assertTrue(report.criticalIssues.any { "check digit" in it.lowercase() })
        assertEquals("❌ VIN norteamericano con check digit inválido", report.dimensionsDetails["VIN"])
    }
}
