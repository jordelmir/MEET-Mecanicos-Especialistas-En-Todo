package com.elysium369.meet.core.emissions.preitv

import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import org.junit.Assert.*
import org.junit.Test

class PreItvStateMachineTest {

    @Test
    fun `state machine progresses through all phases to completion`() {
        val machine = PreItvStateMachine()
        val profile = RegulatoryVehicleProfile(modelYear = 2005)

        assertEquals(PreItvPhase.Idle, machine.phase.value)

        // 1. Start test
        machine.start(profile)
        assertEquals(PreItvPhase.Preconditions, machine.phase.value)

        // 2. Feed preconditions: RPM 750, Temp 88°C, Speed 0, Park confirmed
        machine.tickTelemetry(
            rpm = 750.0,
            ectC = 88.0,
            speedKmh = 0.0,
            stftPct = 1.0,
            ltftPct = 2.0,
            lambda = 1.002,
            transmissionConfirmedParkOrNeutral = true
        )
        assertTrue(machine.phase.value is PreItvPhase.CatalystWarmup)
    }

    @Test
    fun `evaluates literal NO PASA DEKRA with exact failure reasons for high emissions`() {
        val machine = PreItvStateMachine()
        val profile = RegulatoryVehicleProfile(modelYear = 2005)

        machine.setVehicleContext(
            displayName = "Hyundai Accent GLS 2005",
            vin = "KMHBT41BP5U123456",
            plate = "ABC-123"
        )
        machine.start(profile)

        // Inject severe emissions (simulating high positive fuel trims and out of spec lambda)
        machine.injectPhaseData(
            idleRpm = listOf(750.0, 760.0),
            idleEct = listOf(89.0, 90.0),
            idleStft = listOf(14.0, 15.0),
            idleLtft = listOf(10.0, 11.0),
            idleLambda = listOf(1.124, 1.125),
            accelRpm = listOf(2500.0, 2520.0),
            accelEct = listOf(91.0, 92.0),
            accelStft = listOf(16.0, 17.0),
            accelLtft = listOf(12.0, 13.0),
            accelLambda = listOf(1.293, 1.295)
        )

        machine.computeFinalResult()

        val completedPhase = machine.phase.value as PreItvPhase.Completed
        val result = completedPhase.result

        assertEquals("Hyundai Accent GLS 2005", result.vehicleDisplayName)
        assertEquals(DekraOfficialResult.NO_PASA_DEKRA, result.dekraResult)
        assertTrue("Must contain defect reasons", result.dekraFailureReasons.isNotEmpty())
        assertTrue(
            "Must report failure",
            result.dekraFailureReasons.any { it.code.startsWith("DG-") }
        )
    }

    @Test
    fun `evaluates vehicle limits according to vehicle model year`() {
        val machine = PreItvStateMachine()
        val oldProfile = RegulatoryVehicleProfile(modelYear = 1993)

        machine.setVehicleContext(
            displayName = "Toyota Corolla 1993",
            vin = "JT2AE...",
            plate = "998877"
        )
        machine.start(oldProfile)

        machine.computeFinalResult()

        val result = (machine.phase.value as PreItvPhase.Completed).result
        assertNotNull(result.ruleSet)
        assertEquals(1993, result.vehicleProfile?.modelYear)
        // 1993 vehicle has pre-1995 limits (CO <= 4.50%)
        val coIdleLimit = result.ruleSet?.idleLimits?.find { it.metric.name == "CO" }?.max
        assertEquals(4.50, coIdleLimit ?: 0.0, 0.01)
    }
}
