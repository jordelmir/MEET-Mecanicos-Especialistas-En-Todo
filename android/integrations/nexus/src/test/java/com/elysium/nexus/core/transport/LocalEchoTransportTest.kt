package com.elysium.nexus.core.transport

import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LocalEchoTransport] — the
 * test-friendly transport that records every
 * frame instead of sending to a real host.
 *
 * The echo is the test surface for the
 * engine→transport pipeline. The activity
 * wires the echo as the default transport; the
 * `TransportSelector` UI (Phase 1.14) lets the
 * user pick a different transport at runtime.
 */
class LocalEchoTransportTest {

    @Test
    fun lifecycleAdvancesThroughStates() = runTest {
        val transport = LocalEchoTransport()
        assertEquals(TransportState.IDLE, transport.state)
        transport.start()
        assertEquals(TransportState.INITIALISING, transport.state)
        transport.pair()
        assertEquals(TransportState.PAIRED, transport.state)
        transport.connect()
        assertEquals(TransportState.CONNECTED, transport.state)
        transport.disconnect()
        assertEquals(TransportState.DISCONNECTED, transport.state)
        transport.stop()
        assertEquals(TransportState.IDLE, transport.state)
    }

    @Test
    fun sendRealtimeRecordsEveryFrame() = runTest {
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        val state1 = UniversalControllerState.neutral()
        val state2 = state1.copy(sequence = 1uL)
        transport.sendRealtime(state1)
        transport.sendRealtime(state2)
        assertEquals(2, transport.recordedCount())
        assertEquals(state1, transport.recordedAt(0))
        assertEquals(state2, transport.recordedAt(1))
    }

    @Test
    fun sendReliableRecordsEveryEvent() = runTest {
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        transport.sendReliable(ReliableInputEvent.ReleaseAll)
        transport.sendReliable(ReliableInputEvent.ProfileChanged(0))
        assertEquals(2, transport.reliableCount())
        assertEquals(ReliableInputEvent.ReleaseAll, transport.reliableAt(0))
        assertEquals(ReliableInputEvent.ProfileChanged(0), transport.reliableAt(1))
    }

    @Test
    fun releaseAllIsReliable() = runTest {
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        transport.releaseAll()
        assertEquals(1, transport.reliableCount())
        assertEquals(ReliableInputEvent.ReleaseAll, transport.reliableAt(0))
    }

    @Test
    fun capabilitiesAreZeroLatency() {
        val transport = LocalEchoTransport()
        assertEquals(0, transport.capabilities.latencyMs)
        assertTrue(transport.capabilities.supportsReliable)
        assertTrue(transport.capabilities.maxRealtimeFps > 0)
    }

    @Test
    fun clearResetsRecorded() = runTest {
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        transport.sendRealtime(UniversalControllerState.neutral())
        assertEquals(1, transport.recordedCount())
        transport.clear()
        assertEquals(0, transport.recordedCount())
    }
}
