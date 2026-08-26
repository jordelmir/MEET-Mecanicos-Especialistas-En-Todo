package com.elysium369.meet.ride.demand

import org.junit.Assert.*
import org.junit.Test

class RideDemandLevelTest {

    @Test
    fun classify_returns_NORMAL_when_no_requests_and_no_drivers() {
        assertEquals(RideDemandLevel.NORMAL, RideDemandLevel.classify(0, 0))
    }

    @Test
    fun classify_returns_CRITICAL_when_requests_exist_but_no_drivers() {
        assertEquals(RideDemandLevel.CRITICAL, RideDemandLevel.classify(5, 0))
    }

    @Test
    fun classify_returns_NORMAL_when_ratio_is_low() {
        assertEquals(RideDemandLevel.NORMAL, RideDemandLevel.classify(3, 10))
    }

    @Test
    fun classify_returns_BUSY_when_ratio_exceeds_1_5() {
        assertEquals(RideDemandLevel.BUSY, RideDemandLevel.classify(8, 5))
    }

    @Test
    fun classify_returns_HIGH_when_ratio_exceeds_3() {
        assertEquals(RideDemandLevel.HIGH, RideDemandLevel.classify(15, 5))
    }

    @Test
    fun classify_returns_CRITICAL_when_ratio_exceeds_5() {
        assertEquals(RideDemandLevel.CRITICAL, RideDemandLevel.classify(25, 5))
    }
}
