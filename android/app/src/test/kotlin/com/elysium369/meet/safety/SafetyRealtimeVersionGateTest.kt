package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.AggregateVersionGate
import org.junit.Assert.*
import org.junit.Test

class SafetyRealtimeVersionGateTest {

    private val gate = AggregateVersionGate()

    @Test
    fun `shouldApply when incoming greater than local`() {
        assertTrue(gate.shouldApply(incomingVersion = 5, localVersion = 4))
    }

    @Test
    fun `shouldApply false when incoming less than or equal to local`() {
        assertFalse(gate.shouldApply(incomingVersion = 4, localVersion = 4))
        assertFalse(gate.shouldApply(incomingVersion = 3, localVersion = 4))
    }

    @Test
    fun `hasGap when incoming greater than local + 1`() {
        assertTrue(gate.hasGap(incomingVersion = 6, localVersion = 4))
    }

    @Test
    fun `hasGap false when incoming equals local + 1`() {
        assertFalse(gate.hasGap(incomingVersion = 5, localVersion = 4))
    }

    @Test
    fun `hasGap false when incoming less than or equal to local`() {
        assertFalse(gate.hasGap(incomingVersion = 4, localVersion = 4))
        assertFalse(gate.hasGap(incomingVersion = 3, localVersion = 4))
    }
}
