package com.elysium369.meet.core.evair.safety

import com.elysium369.meet.core.evair.domain.ActionRisk
import com.elysium369.meet.core.evair.domain.ConnectionSnapshot
import com.elysium369.meet.core.evair.domain.ElectricalSnapshot
import com.elysium369.meet.core.evair.domain.EmissionsSnapshot
import com.elysium369.meet.core.evair.domain.EngineSnapshot
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.domain.FuelSnapshot
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleDataSource
import com.elysium369.meet.core.evair.domain.VehicleIdentity
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.obd.PhysicalBusOwner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleActionExecutorTest {

    private val safetyBroker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
    private val executor = VehicleActionExecutor(safetyBroker = safetyBroker)

    @Test
    fun `executor denies active command when vehicle is moving`() = runBlocking {
        val movingSnapshot = createSnapshot(speedKph = 45.0)
        val action = ProposedVehicleAction(
            actionId = "test_injector_cut",
            command = VehicleCommand.RunDiagnosticTest(requestId = "req_1", testId = "injector_cut"),
            reason = "Test cylinder injector balance",
            expectedObservation = "RPM drop if cylinder is contributing",
            risk = ActionRisk.HIGH
        )

        val result = executor.executeAction(action, movingSnapshot, userConfirmed = true)
        assertTrue(result is EvairResult.Failure)
    }

    @Test
    fun `executor requires explicit user confirmation for active tests`() = runBlocking {
        val stationarySnapshot = createSnapshot(speedKph = 0.0)
        val action = ProposedVehicleAction(
            actionId = "test_purge_valve",
            command = VehicleCommand.RunDiagnosticTest(requestId = "req_2", testId = "purge_valve"),
            reason = "Actuate EVAP purge solenoid",
            expectedObservation = "Clicking sound and manifold pressure change",
            risk = ActionRisk.MEDIUM
        )

        val resultWithoutConfirm = executor.executeAction(action, stationarySnapshot, userConfirmed = false)
        assertTrue(resultWithoutConfirm is EvairResult.Failure)

        val resultWithConfirm = executor.executeAction(action, stationarySnapshot, userConfirmed = true)
        assertTrue(resultWithConfirm is EvairResult.Success)
    }

    private fun createSnapshot(speedKph: Double): VehicleSnapshot {
        return VehicleSnapshot(
            timestampMs = 1771234567890L,
            monotonicTimestampNs = 123456L,
            vehicle = VehicleIdentity("VIN123", "VIN123", "Hyundai", "Accent", 2005, "1.6L G4ED", "AT", "Accent"),
            connection = ConnectionSnapshot("CONNECTED", true, "CAN", "OPTIMAL", "BT", 25L),
            engine = EngineSnapshot(rpm = 780.0, speedKph = speedKph),
            electrical = ElectricalSnapshot(14.2, 14.2),
            fuel = FuelSnapshot(stftBank1Pct = 2.0, ltftBank1Pct = 3.0),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.REAL_OBD
        )
    }
}
