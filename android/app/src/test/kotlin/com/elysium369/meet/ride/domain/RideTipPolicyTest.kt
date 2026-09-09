package com.elysium369.meet.ride.domain

import org.junit.Assert.*
import org.junit.Test

class RideTipPolicyTest {
    @Test fun `display and custom entry agree with command currency units`() {
        assertEquals("500", RideTipPolicy.display(500, "CRC"))
        assertEquals("1.00", RideTipPolicy.display(100, "USD"))
        assertEquals(500L, RideTipPolicy.parseMajor("500", "CRC"))
        assertEquals(100L, RideTipPolicy.parseMajor("1", "USD"))
    }

    @Test fun `invalid and overflowing tips never become commands`() {
        for (value in listOf("", "-1", "0", "100001", "999999999999999999999999")) {
            assertNull(value, RideTipPolicy.parseMajor(value, "CRC"))
        }
        assertNull(RideTipPolicy.parseMajor("0.001", "USD"))
        assertFalse(RideTipPolicy.isValid(100, "EUR"))
        assertTrue(RideTipPolicy.isValid(100_000, "CRC"))
    }
}
