package com.elysium369.meet.core.evair.agent

import com.elysium369.meet.core.diagnostics.DiagnosticReasoningEngine
import com.elysium369.meet.core.evair.domain.ConnectionSnapshot
import com.elysium369.meet.core.evair.domain.DiagnosticAgentRequest
import com.elysium369.meet.core.evair.domain.DiagnosticSeverity
import com.elysium369.meet.core.evair.domain.DiagnosticTrigger
import com.elysium369.meet.core.evair.domain.DtcCategory
import com.elysium369.meet.core.evair.domain.DtcSnapshot
import com.elysium369.meet.core.evair.domain.ElectricalSnapshot
import com.elysium369.meet.core.evair.domain.EmissionsSnapshot
import com.elysium369.meet.core.evair.domain.EngineSnapshot
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.domain.FuelSnapshot
import com.elysium369.meet.core.evair.domain.VehicleDataSource
import com.elysium369.meet.core.evair.domain.VehicleIdentity
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityGatewayTest {

    private val reasoningEngine = DiagnosticReasoningEngine(now = { 1000L })
    private val gateway = AntigravityGateway(reasoningEngine)

    @Test
    fun `gateway produces deterministic hypothesis for P0301 misfire`() = runBlocking {
        val dtcs = listOf(
            DtcSnapshot("P0301", DtcCategory.CONFIRMED, "Cylinder 1 Misfire Detected")
        )
        val snapshot = createSnapshot(dtcs)
        val request = DiagnosticAgentRequest(
            requestId = "req_diag_p0301",
            vehicleId = "VIN_ACCENT_2005",
            trigger = DiagnosticTrigger.DTC_APPEARED,
            snapshot = snapshot
        )

        val result = gateway.diagnose(request)
        assertTrue(result is EvairResult.Success)

        val diagnostic = (result as EvairResult.Success).value
        assertEquals("req_diag_p0301", diagnostic.requestId)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertTrue(diagnostic.hypotheses.isNotEmpty())

        val topHypothesis = diagnostic.hypotheses.first()
        assertTrue(topHypothesis.cause.contains("bujía", ignoreCase = true) || topHypothesis.cause.contains("cilindro 1", ignoreCase = true))

        // Must propose non-destructive discriminating test
        assertTrue(diagnostic.recommendedTests.isNotEmpty())
        val swapTest = diagnostic.recommendedTests.find { it.testId.contains("COIL") || it.testId.contains("SWAP") }
        assertNotNull(swapTest)
    }

    @Test
    fun `gateway reports honest no DTC observed status when no DTCs are present`() = runBlocking {
        val snapshot = createSnapshot(emptyList())
        val request = DiagnosticAgentRequest(
            requestId = "req_diag_nominal",
            vehicleId = "VIN_ACCENT_2005",
            trigger = DiagnosticTrigger.USER_REQUEST,
            snapshot = snapshot
        )

        val result = gateway.diagnose(request)
        assertTrue(result is EvairResult.Success)
        val diagnostic = (result as EvairResult.Success).value
        assertEquals(DiagnosticSeverity.INFO, diagnostic.severity)
        assertTrue(diagnostic.summary.contains("No se observaron códigos DTC", ignoreCase = true))
        assertTrue(diagnostic.hypotheses.first().cause.contains("No se observaron códigos de falla", ignoreCase = true))
    }

    private fun createSnapshot(dtcs: List<DtcSnapshot>): VehicleSnapshot {
        return VehicleSnapshot(
            timestampMs = 1771234567890L,
            monotonicTimestampNs = 123456L,
            vehicle = VehicleIdentity("VIN123", "VIN123", "Hyundai", "Accent", 2005, "1.6L G4ED", "AT", "Accent"),
            connection = ConnectionSnapshot("CONNECTED", true, "CAN", "OPTIMAL", "BT", 25L),
            engine = EngineSnapshot(rpm = 780.0, speedKph = 0.0),
            electrical = ElectricalSnapshot(14.2, 14.2),
            fuel = FuelSnapshot(stftBank1Pct = 2.0, ltftBank1Pct = 3.0),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = dtcs,
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.REAL_OBD
        )
    }
}
