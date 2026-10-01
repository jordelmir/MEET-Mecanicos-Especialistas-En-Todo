package com.elysium369.meet.audio.supreme

import org.junit.Assert.assertEquals
import org.junit.Test

class SupremeBoostPolicyTest {
    @Test fun `requested amplitude converts to Android millibels instead of multiplying percentages as decibels`() {
        assertEquals(0, SupremeBoostPolicy.millibels(100))
        assertEquals(602, SupremeBoostPolicy.millibels(200))
        assertEquals(1204, SupremeBoostPolicy.millibels(400))
    }
    @Test fun `persisted or external values cannot exceed supported boost bounds`() {
        assertEquals(0, SupremeBoostPolicy.millibels(Int.MIN_VALUE))
        assertEquals(1204, SupremeBoostPolicy.millibels(Int.MAX_VALUE))
    }
}
