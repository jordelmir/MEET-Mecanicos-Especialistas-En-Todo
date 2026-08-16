package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.core.obd.ScanCompleteness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSessionBindingTest {
    @Test
    fun `clear authorization requires explicit confirmation when ecu vin is unavailable`() {
        val binding = VehicleSessionBinding.unbound("session", "physical")

        assertEquals(
            ClearDtcAuthorization.RequiresUserConfirmation,
            binding.authorizeDtcClear(selectedVehicleId = "vehicle-a"),
        )
        assertTrue(binding.authorizeDtcClear(selectedVehicleId = null) is ClearDtcAuthorization.Blocked)
    }

    @Test
    fun `clear authorization permits only the selected bound vehicle and blocks conflicts`() {
        val unbound = VehicleSessionBinding.unbound("session", "physical")
        val confirmed = unbound.bindUserConfirmed(
            vehicleId = "vehicle-a",
            expectedVin = null,
            evidenceId = "explicit-user-confirmation",
        )

        assertEquals(ClearDtcAuthorization.Authorized, confirmed.authorizeDtcClear("vehicle-a"))
        assertTrue(confirmed.authorizeDtcClear("vehicle-b") is ClearDtcAuthorization.Blocked)

        val conflict = unbound.bindVerifiedVin(
            "vehicle-a",
            "KMHCG45C51U123456",
            "KMHCG45C51U654321",
            "vin-read",
        )
        assertTrue(conflict.authorizeDtcClear("vehicle-a") is ClearDtcAuthorization.Blocked)
    }

    @Test
    fun `unbound and conflicted sessions cannot persist or actuate`() {
        val unbound = VehicleSessionBinding.unbound("session", "physical")
        assertFalse(unbound.allowsPersistence)
        assertFalse(unbound.allowsActiveOperations)

        val conflict = unbound.bindVerifiedVin(
            "vehicle-a",
            "KMHCG45C51U123456",
            "KMHCG45C51U654321",
            "vin-read",
        )
        assertFalse(conflict.allowsPersistence)
        assertFalse(conflict.allowsActiveOperations)
    }

    @Test
    fun `latest scan is visible only to its bound vehicle`() {
        val projection = LatestDiagnosticScanProjection(
            scanId = "scan-a",
            sessionId = "session-a",
            vehicleBindingId = "binding-a",
            vehicleId = "vehicle-a",
            findings = emptyList(),
            completeness = ScanCompleteness.COMPLETE,
            capturedAt = 1L,
        )
        assertTrue(projection.belongsTo("vehicle-a"))
        assertFalse(projection.belongsTo("vehicle-b"))
        assertFalse(projection.belongsTo(null))
    }
}
