package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideGuardianPolicyTest {
    @Test
    fun `guardian only signals on confirmed active server states`() {
        assertTrue(RideGuardianPolicy.canSignal("IN_PROGRESS", 7))
        assertTrue(RideGuardianPolicy.canSignal("ASSIGNED", 1))
        assertFalse(RideGuardianPolicy.canSignal("COMPLETED", 8))
        assertFalse(RideGuardianPolicy.canSignal("IN_PROGRESS", 0))
        assertFalse(RideGuardianPolicy.canSignal(null, 1))
    }

    @Test
    fun `sos and collision are critical`() {
        assertEquals("CRITICAL", RideGuardianPolicy.severity(RideSafetySignalType.SOS))
        assertEquals(
            "CRITICAL",
            RideGuardianPolicy.severity(RideSafetySignalType.POSSIBLE_COLLISION),
        )
        assertEquals(
            "CHECK_IN",
            RideGuardianPolicy.severity(RideSafetySignalType.SIGNAL_LOSS),
        )
    }
}
