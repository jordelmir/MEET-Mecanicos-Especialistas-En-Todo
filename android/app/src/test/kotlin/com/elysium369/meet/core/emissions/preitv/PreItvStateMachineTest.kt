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

        // 3. Fast-forward warmup (simulate ticks over 25 seconds)
        Thread.sleep(50) // small delay to let monotonic time advance
        // Tick warmup to exceed 20s target
        for (i in 0..25) {
            machine.tickTelemetry(
                rpm = 2600.0,
                ectC = 90.0,
                speedKmh = 0.0,
                stftPct = 1.0,
                ltftPct = 2.0,
                lambda = 1.001
            )
        }
    }
}
