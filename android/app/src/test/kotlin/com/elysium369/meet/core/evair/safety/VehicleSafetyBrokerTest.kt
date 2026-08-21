package com.elysium369.meet.core.evair.safety

import com.elysium369.meet.core.evair.domain.ActionRisk
import com.elysium369.meet.core.evair.domain.AuthorizationResult
import com.elysium369.meet.core.evair.domain.ConnectionSnapshot
import com.elysium369.meet.core.evair.domain.ElectricalSnapshot
import com.elysium369.meet.core.evair.domain.EmissionsSnapshot
import com.elysium369.meet.core.evair.domain.EngineSnapshot
import com.elysium369.meet.core.evair.domain.FuelSnapshot
import com.elysium369.meet.core.evair.domain.ProposedVehicleAction
import com.elysium369.meet.core.evair.domain.VehicleCommand
import com.elysium369.meet.core.evair.domain.VehicleDataSource
import com.elysium369.meet.core.evair.domain.VehicleIdentity
import com.elysium369.meet.core.evair.domain.VehicleSnapshot
import com.elysium369.meet.core.obd.PhysicalBusOwner
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSafetyBrokerTest {

    @Test
    fun `read-only PID commands are automatically authorized`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
        val proposal = ProposedVehicleAction(
            actionId = "act_1",
            command = VehicleCommand.ReadPid(requestId = "req_1", pid = "010C"),
            reason = "Checking idle RPM",
            expectedObservation = "RPM around 750",
            risk = ActionRisk.NONE
        )

        val result = broker.authorize(proposal)
        assertTrue(result is AuthorizationResult.Allowed)
        assertTrue(broker.isAutoExecutable(proposal.command))
    }

    @Test
    fun `active tests when vehicle is moving are DENIED`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
        val proposal = ProposedVehicleAction(
            actionId = "act_2",
            command = VehicleCommand.RunDiagnosticTest(
                requestId = "req_2",
                testId = "EVAP_PURGE_VALVE_CYCLE"
            ),
            reason = "Testing EVAP valve",
            expectedObservation = "Valve clicks",
            risk = ActionRisk.MEDIUM
        )

        val movingSnapshot = createSnapshot(speedKph = 45.0)
        val result = broker.authorize(proposal, movingSnapshot)

        assertTrue(result is AuthorizationResult.Denied)
        val denied = result as AuthorizationResult.Denied
        assertTrue(denied.reason.contains("movimiento"))
    }

    @Test
    fun `active tests when bus is occupied by oscilloscope are DENIED`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.OSCILLOSCOPE })
        val proposal = ProposedVehicleAction(
            actionId = "act_3",
            command = VehicleCommand.RunDiagnosticTest(
                requestId = "req_3",
                testId = "FUEL_PUMP_RELAY_TEST"
            ),
            reason = "Testing fuel pump relay",
            expectedObservation = "Pump primes",
            risk = ActionRisk.MEDIUM
        )

        val stoppedSnapshot = createSnapshot(speedKph = 0.0)
        val result = broker.authorize(proposal, stoppedSnapshot)

        assertTrue(result is AuthorizationResult.Denied)
        val denied = result as AuthorizationResult.Denied
        assertTrue(denied.reason.contains("OSCILLOSCOPE"))
    }

    @Test
    fun `active tests when vehicle is stopped require confirmation`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
        val proposal = ProposedVehicleAction(
            actionId = "act_4",
            command = VehicleCommand.RunDiagnosticTest(
                requestId = "req_4",
                testId = "FAN_HIGH_SPEED_RELAY"
            ),
            reason = "Testing radiator fan",
            expectedObservation = "Fan turns on",
            risk = ActionRisk.MEDIUM
        )

        val stoppedSnapshot = createSnapshot(speedKph = 0.0)
        val result = broker.authorize(proposal, stoppedSnapshot)

        assertTrue(result is AuthorizationResult.RequiresConfirmation)
    }

    @Test
    fun `clear DTCs command always requires user confirmation`() {
        val broker = VehicleSafetyBroker(getPhysicalBusOwner = { PhysicalBusOwner.IDLE })
        val proposal = ProposedVehicleAction(
            actionId = "act_5",
            command = VehicleCommand.ClearDtcs(requestId = "req_5"),
            reason = "Clearing pending codes after fix",
            expectedObservation = "MIL turns off",
            risk = ActionRisk.HIGH
        )

        val result = broker.authorize(proposal)
        assertTrue(result is AuthorizationResult.RequiresConfirmation)
    }

    private fun createSnapshot(speedKph: Double): VehicleSnapshot {
        return VehicleSnapshot(
            timestampMs = System.currentTimeMillis(),
            monotonicTimestampNs = 0L,
            vehicle = VehicleIdentity(
                vehicleId = "VIN123",
                vin = "VIN123",
                make = "Hyundai",
                model = "Accent",
                year = 2005,
                engineType = "1.6L G4ED",
                transmissionType = "AT",
                label = "Test Car"
            ),
            connection = ConnectionSnapshot(
                phase = "CONNECTED",
                hasRealEcuLink = true,
                protocol = "ISO 15765-4",
                adapterQuality = "OPTIMAL",
                transport = "BT_CLASSIC",
                latencyMs = 30L
            ),
            engine = EngineSnapshot(
                rpm = 750.0,
                speedKph = speedKph
            ),
            electrical = ElectricalSnapshot(controlModuleVoltage = 14.1),
            fuel = FuelSnapshot(),
            transmission = null,
            emissions = EmissionsSnapshot(),
            dtcs = emptyList(),
            readiness = emptyMap(),
            activeWarnings = emptyList(),
            dataSource = VehicleDataSource.REAL_OBD
        )
    }
}
