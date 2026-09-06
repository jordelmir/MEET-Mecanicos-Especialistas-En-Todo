package com.elysium369.meet.mobility.domain.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class AggregateVersionGateTest {

    private val gate = AggregateVersionGate()

    @Test
    fun evaluates_sequential_version_as_Apply() {
        assertEquals(VersionDecision.Apply, gate.evaluate(current = 1L, incoming = 2L))
        assertEquals(VersionDecision.Apply, gate.evaluate(current = 0L, incoming = 1L))
        assertEquals(VersionDecision.Apply, gate.evaluate(current = 42L, incoming = 43L))
    }

    @Test
    fun evaluates_duplicate_or_stale_version_as_Ignore() {
        assertEquals(VersionDecision.Ignore, gate.evaluate(current = 5L, incoming = 5L))
        assertEquals(VersionDecision.Ignore, gate.evaluate(current = 5L, incoming = 4L))
        assertEquals(VersionDecision.Ignore, gate.evaluate(current = 5L, incoming = 0L))
    }

    @Test
    fun evaluates_version_gap_as_Resync() {
        assertEquals(VersionDecision.Resync, gate.evaluate(current = 1L, incoming = 3L))
        assertEquals(VersionDecision.Resync, gate.evaluate(current = 10L, incoming = 15L))
    }
}
