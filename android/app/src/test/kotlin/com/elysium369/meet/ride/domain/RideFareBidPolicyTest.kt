package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RideFareBidPolicyTest {
    @Test
    fun `CRC fares always snap to three hundred colones`() {
        assertEquals(2_400.0, RideFareBidPolicy.normalize(2_500.0, "CRC"), 0.0)
        assertEquals(2_700.0, RideFareBidPolicy.adjust(2_400.0, "CRC", 1), 0.0)
        assertEquals(2_100.0, RideFareBidPolicy.adjust(2_400.0, "CRC", -1), 0.0)
    }

    @Test
    fun `CRC floor and ceiling are enforced`() {
        assertEquals(900.0, RideFareBidPolicy.normalize(1.0, "CRC"), 0.0)
        assertEquals(30_000.0, RideFareBidPolicy.normalize(90_000.0, "CRC"), 0.0)
    }
}
