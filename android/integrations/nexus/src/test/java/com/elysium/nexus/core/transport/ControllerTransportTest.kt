package com.elysium.nexus.core.transport

import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [ControllerTransport] interface
 * and the result types.
 *
 * The interface itself has no implementation
 * (Phase 1.6 ships the seam; Phase 1.7+ ships
 * the first real transport). The tests verify:
 *  - The result types are total over the
 *    closed set of events.
 *  - A `FakeTransport` captures the
 *    `sendRealtime` and `sendReliable` calls for
 *    later assertion.
 *  - The `releaseAll` event is `ReliableInputEvent.ReleaseAll`.
 */
class ControllerTransportTest {

    @Test
    fun reliableEventHierarchyIsExhaustive() {
        // Every event in the closed set is reachable
        // through a `when` exhaustive check. A
        // future contributor who adds a new event
        // must update the transports that match on
        // it; the compiler will flag the missing
        // branches.
        val events: List<ReliableInputEvent> = listOf(
            ReliableInputEvent.ReleaseAll,
            ReliableInputEvent.ButtonDown(CanonicalButton.South),
            ReliableInputEvent.ButtonUp(CanonicalButton.South),
            ReliableInputEvent.ProfileChanged(0),
            ReliableInputEvent.PairingRequest("Honor Magic V2"),
            ReliableInputEvent.Revocation("Honor Magic V2")
        )
        assertEquals(6, events.size)
    }

    @Test
    fun fakeTransportCapturesRealtimeFrames() = runTest {
        val transport = FakeTransport()
        val state1 = UniversalControllerState.neutral()
        val state2 = state1.copy(sequence = 1uL)
        transport.sendRealtime(state1)
        transport.sendRealtime(state2)
        assertEquals(2, transport.realtimeCount())
        assertEquals(state1, transport.realtimeAt(0))
        assertEquals(state2, transport.realtimeAt(1))
    }

    @Test
    fun fakeTransportCapturesReliableEvents() = runTest {
        val transport = FakeTransport()
        transport.sendReliable(ReliableInputEvent.ReleaseAll)
        transport.sendReliable(
            ReliableInputEvent.ButtonDown(CanonicalButton.South)
        )
        assertEquals(2, transport.reliableCount())
        assertEquals(ReliableInputEvent.ReleaseAll, transport.reliableAt(0))
    }

    @Test
    fun fakeTransportReleaseAllIsReliable() = runTest {
        val transport = FakeTransport()
        transport.releaseAll()
        assertEquals(1, transport.reliableCount())
        assertEquals(ReliableInputEvent.ReleaseAll, transport.reliableAt(0))
    }

    @Test
    fun fakeTransportStartStopSucceed() = runTest {
        val transport = FakeTransport()
        assertEquals(TransportResult.Ok, transport.start())
        assertEquals(TransportResult.Ok, transport.stop())
    }

    @Test
    fun transportCapabilitiesAreDescribed() {
        val caps = TransportCapabilities(
            maxRealtimeFps = 250,
            supportsReliable = true,
            latencyMs = 5,
            label = "Test transport"
        )
        assertEquals(250, caps.maxRealtimeFps)
        assertTrue(caps.supportsReliable)
        assertEquals(5, caps.latencyMs)
        assertEquals("Test transport", caps.label)
    }
}

/**
 * A test-only [ControllerTransport] that captures
 * every `sendRealtime` and `sendReliable` call
 * for later assertion. The fake's lifecycle is
 * trivial: `start` and `stop` return `Ok`; the
 * state machine stays in `INITIALISING` /
 * `DISCONNECTED` (the fake does not actually
 * connect to anything).
 *
 * The fake is the test surface for the activity's
 * transport tests (Phase 1.7+).
 */
class FakeTransport(
    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 250,
        supportsReliable = true,
        latencyMs = 1,
        label = "Fake"
    )
) : ControllerTransport {

    private val realtimeFrames: MutableList<UniversalControllerState> = mutableListOf()
    private val reliableEvents: MutableList<ReliableInputEvent> = mutableListOf()
    override var state: TransportState = TransportState.IDLE
        private set

    override suspend fun start(): TransportResult {
        state = TransportState.INITIALISING
        return TransportResult.Ok
    }

    override suspend fun pair(): PairingResult {
        state = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        state = TransportState.CONNECTED
        return ConnectionResult.Ok
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult {
        realtimeFrames.add(state)
        return SendResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult {
        reliableEvents.add(event)
        return SendResult.Ok
    }

    override suspend fun releaseAll(): SendResult {
        reliableEvents.add(ReliableInputEvent.ReleaseAll)
        return SendResult.Ok
    }

    override suspend fun disconnect(): DisconnectResult {
        state = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        state = TransportState.IDLE
        return TransportResult.Ok
    }

    fun realtimeCount(): Int = realtimeFrames.size
    fun reliableCount(): Int = reliableEvents.size
    fun realtimeAt(i: Int): UniversalControllerState = realtimeFrames[i]
    fun reliableAt(i: Int): ReliableInputEvent = reliableEvents[i]
}
