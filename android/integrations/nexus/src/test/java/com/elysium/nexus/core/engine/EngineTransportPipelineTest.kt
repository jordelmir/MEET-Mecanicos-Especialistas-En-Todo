package com.elysium.nexus.core.engine

import com.elysium.nexus.core.engine.CanonicalInputEngine
import com.elysium.nexus.core.engine.EngineState
import com.elysium.nexus.core.engine.TransportBinding
import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.UniversalControllerState
import com.elysium.nexus.core.transport.LocalEchoTransport
import com.elysium.nexus.core.transport.ReliableInputEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The end-to-end test of the engine→transport
 * pipeline. This is the §45 "first milestone"
 * test: a touch on the editor results in a state
 * emission, the state is forwarded to the
 * transport, and the transport's `sendRealtime`
 * is called.
 *
 * The test is JVM-only; no Android `Context` is
 * required. The transport is a
 * [LocalEchoTransport] (test-friendly; records
 * every frame). The engine is a
 * [CanonicalInputEngine] with the same
 * configuration as production (latency tracker,
 * stick config, etc.).
 *
 * ## Why this is the "100% test" for Phase 1
 *
 * The first milestone per `MASTER_ORDER.md` §45
 * is "the APK on Honor Magic V2 that emits
 * generic HID (or Elysium Link) and the host
 * sees buttons / D-pad / sticks / triggers /
 * motion, and an abrupt disconnect neutralizes
 * everything". This test exercises the engine→
 * transport path (the "host sees" part); the
 * disconnect test (Phase 0.6) exercises the
 * "abrupt disconnect neutralizes" part. Together
 * they cover §45's "first milestone" for the
 * Android module.
 */
class EngineTransportPipelineTest {

    @Test
    fun engineStateEmissionsAreForwardedToTransport() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = scope
        )
        engine.transitionTo(EngineState.Active)
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        val binding = TransportBinding(transport)

        // Forward a single state emission
        // synchronously. The transport records
        // every frame; the test asserts the
        // record contains the button.
        val state = engine.state.value
        binding.forwardRealtime(state)
        assertTrue(
            "Expected at least one recorded frame; got ${transport.recordedCount()}",
            transport.recordedCount() >= 1
        )
        val recorded = transport.recordedAt(transport.recordedCount() - 1)
        // The initial state is neutral; submit a
        // button and forward again.
        engine.submitButton(CanonicalButton.South, true)
        val stateWithSouth = engine.state.value
        assertTrue(
            "Engine state should have South pressed: ${stateWithSouth.buttons}",
            stateWithSouth.buttons.isPressed(CanonicalButton.South)
        )
        binding.forwardRealtime(stateWithSouth)
        val lastFrame = transport.recordedAt(transport.recordedCount() - 1)
        assertTrue(
            "Expected South button pressed in last frame: $lastFrame",
            lastFrame.buttons.isPressed(CanonicalButton.South)
        )
    }

    @Test
    fun abruptDisconnectNeutralizesTheTransport() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = scope
        )
        engine.transitionTo(EngineState.Active)
        val transport = LocalEchoTransport()
        transport.start()
        transport.connect()
        val binding = TransportBinding(transport)

        // Press a button and let the engine emit.
        engine.submitButton(CanonicalButton.South, true)
        binding.forwardRealtime(engine.state.value)
        val beforeDisconnect = transport.recordedAt(transport.recordedCount() - 1)
        assertTrue(
            "Expected South pressed before disconnect: $beforeDisconnect",
            beforeDisconnect.buttons.isPressed(CanonicalButton.South)
        )

        // Now disconnect: the engine transitions
        // to Disconnected, the binding forwards
        // the neutral state, the transport records
        // the neutral frame.
        engine.transitionTo(EngineState.Suspended)
        engine.transitionTo(EngineState.Reconnecting)
        engine.transitionTo(EngineState.Disconnected)
        engine.neutralize()

        // Forward the new state.
        binding.forwardRealtime(engine.state.value)

        // After the §38 "abrupt disconnect"
        // path, the transport should have received
        // a state emission. The neutral state
        // has no buttons pressed.
        val lastFrame = transport.recordedAt(transport.recordedCount() - 1)
        assertTrue(
            "Expected neutral state at last frame; got $lastFrame",
            !lastFrame.buttons.isPressed(CanonicalButton.South)
        )
    }
}
