package com.elysium.nexus.core.engine

import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.MotionState
import com.elysium.nexus.core.model.StickState
import com.elysium.nexus.core.model.TouchPoint
import com.elysium.nexus.core.model.TriggerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §38 disconnect test.
 *
 * `MASTER_ORDER.md` §38 is the *release-blocker* test. A single
 * stuck control on disconnect is grounds for rejection. The full
 * §38 spec includes keys, mouse, and "ghost devices" which are
 * transport-layer concerns and are tested in Phase 2+. 0.6 ships
 * the engine's half:
 *
 * > 1. Presionar 4 botones simultáneos.
 * > 2. Mantener ambos triggers.
 * > 3. Mantener ambos sticks fuera del centro.
 * > 4. Mantener D-pad diagonal.
 * > 5. Mantener touchpad activo.
 * > 6. Activar gyro.
 * > 7-8. (keys / mouse — transport, not engine)
 * > 9-12. (Bluetooth / Wi-Fi / process kill / receiver reset —
 * >       transport, not engine)
 * > 13. Verificar en el host: cero botones presionados; sticks
 * >     centrados; triggers en cero; D-pad neutral; touch
 * >     cancelado; teclas liberadas; mouse liberado; dispositivo
 * >     anterior eliminado; ninguna sesión fantasma.
 * > 14. Reconectar.
 * > 15. Verificar que el primer estado sea neutral.
 *
 * The engine's contribution is steps 1-6 (set the state) and the
 * first half of step 13 (engine-side neutralization on
 * transition out of `Active`). The transport layer adds the
 * second half of step 13 and steps 14-15 in Phase 2+.
 *
 * ## Why a separate test class
 *
 * §38 is a release blocker; it gets its own class so it is
 * grep-able from CI logs and so the `*DisconnectTest` name shows
 * up in any test report. The naming is the spec's: the section
 * number, the test name. Future revisions of the spec update
 * the test, not the class name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Section38DisconnectTest {

    /**
     * Build an engine and a recording flow. The flow collects
     * the engine's emitted states so the test can verify the
     * last observed state on disconnect.
     */
    private fun newEngineWithObserver(): Triple<CanonicalInputEngine, MutableSharedFlow<Any>, TestScope> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val engine = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = scope
        )
        val flow = MutableSharedFlow<Any>(extraBufferCapacity = 64)
        engine.state
            .onEach { flow.tryEmit(it) }
            .launchIn(scope)
        return Triple(engine, flow, scope)
    }

    @Test
    fun section38HoldAllInputsThenDisconnectNeutralizes() = runTest {
        val (engine, _, _) = newEngineWithObserver()

        // 1. transition into Active first; the engine refuses
        // submissions in any other state.
        engine.transitionTo(EngineState.Active)

        // 1. Press 4 buttons.
        engine.submitButton(CanonicalButton.South, true)
        engine.submitButton(CanonicalButton.East, true)
        engine.submitButton(CanonicalButton.North, true)
        engine.submitButton(CanonicalButton.West, true)
        // 2. Hold both triggers.
        engine.submitTrigger(StickSide.Left, TriggerState(0.5f))
        engine.submitTrigger(StickSide.Right, TriggerState(0.7f))
        // 3. Both sticks at full corner deflection.
        engine.submitStick(StickSide.Left, StickState(1f, 1f))
        engine.submitStick(StickSide.Right, StickState(-1f, -1f))
        // 4. D-pad diagonal.
        engine.submitDpad(DpadState.NorthEast)
        // 5. Touchpad active (two fingers).
        engine.submitTouchPoint(0, TouchPoint(0, 0.5f, 0.5f, 0.5f))
        engine.submitTouchPoint(1, TouchPoint(1, 0.3f, 0.3f, 0.5f))
        // 6. Gyro active (a sane motion sample).
        engine.submitMotion(
            MotionState(
                gyroX = 0.01f, gyroY = -0.02f, gyroZ = 0.03f,
                accelX = 0f, accelY = 9.81f, accelZ = 0f,
                roll = 0f, pitch = 0f, yaw = 0f,
                sampleTimestampNs = 1uL
            )
        )

        // Sanity check: the state is non-neutral. If this
        // assertion fails, the test setup is wrong.
        val pre = engine.state.value
        assertTrue(
            "pre-disconnect state should be non-neutral, got $pre",
            !pre.buttons.isEmpty() ||
                pre.leftStick != StickState.NEUTRAL ||
                pre.rightStick != StickState.NEUTRAL ||
                pre.leftTrigger != TriggerState.RELEASED ||
                pre.rightTrigger != TriggerState.RELEASED ||
                pre.dpad != DpadState.Center ||
                !pre.touches.isEmpty() ||
                pre.motion != null
        )

        // 11. "Terminate the agent process" — the engine's
        // model of an abrupt disconnect is
        // `transitionTo(Disconnected)`. The transport layer's
        // model of an abrupt disconnect is the host seeing
        // the device disappear; that is Phase 2+ and has its
        // own test class.
        val r = engine.transitionTo(EngineState.Disconnected)
        assertTrue("expected Transitioned, got $r", r is TransitionResult.Transitioned)

        // 13. Verify every input is at neutral.
        val post = engine.state.value
        assertTrue(
            "expected neutral buttons, got ${post.buttons}",
            post.buttons.isEmpty()
        )
        assertEquals("left stick", StickState.NEUTRAL, post.leftStick)
        assertEquals("right stick", StickState.NEUTRAL, post.rightStick)
        assertEquals("left trigger", TriggerState.RELEASED, post.leftTrigger)
        assertEquals("right trigger", TriggerState.RELEASED, post.rightTrigger)
        assertEquals("dpad", DpadState.Center, post.dpad)
        assertTrue("expected empty touches, got ${post.touches}", post.touches.isEmpty())
        assertNull("expected null motion, got ${post.motion}", post.motion)
    }

    @Test
    fun section38NeutralizeWithoutTransitionResetsToZero() = runTest {
        // The §38 step 9-10 "cut Bluetooth and Wi-Fi abruptly"
        // and "apagar la pantalla del Android" can be modelled
        // as an explicit `neutralize()` call. The transport
        // layer will call this on a perceived disconnect. The
        // engine's job is to make sure `neutralize()` resets
        // every input to zero, leaving the host in a known
        // state.
        val (engine, _, _) = newEngineWithObserver()
        engine.transitionTo(EngineState.Active)
        engine.submitButton(CanonicalButton.South, true)
        engine.submitStick(StickSide.Left, StickState(0.5f, 0.5f))
        engine.submitTouchPoint(0, TouchPoint(0, 0.5f, 0.5f, 0.5f))
        engine.submitMotion(
            MotionState(
                gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                accelX = 0f, accelY = 0f, accelZ = 0f,
                roll = 0.5f, pitch = 0.5f, yaw = 0.5f,
                sampleTimestampNs = 1uL
            )
        )
        engine.neutralize()
        val post = engine.state.value
        assertTrue(post.buttons.isEmpty())
        assertEquals(StickState.NEUTRAL, post.leftStick)
        assertEquals(StickState.NEUTRAL, post.rightStick)
        assertEquals(DpadState.Center, post.dpad)
        assertTrue(post.touches.isEmpty())
        // Motion is cleared by `neutralize()`. The §32 spec
        // says "motion recentered if required"; for the
        // abrupt-disconnect path, the engine's "force zero"
        // is the right interpretation — a stuck motion
        // sample is just as bad as a stuck button. A
        // future revision may add a `neutralizeKeepMotion()`
        // variant if a host needs to receive the last motion
        // sample before the disconnect; the §38 contract
        // says "neutralize everything", not "recenter
        // motion to its last known value".
        assertNull(post.motion)
    }

    @Test
    fun section38AllDisconnectedStatesAreNeutral() = runTest {
        // Property: for every EngineState that does not have
        // a live session (Idle, Discovering, Pairing,
        // Authenticating, Negotiating, Disconnected), the
        // engine's current state is neutral. The "Active"
        // state is the only state in which non-neutral
        // input is held.
        val (engine, _, _) = newEngineWithObserver()
        val nonEmitting = listOf(
            EngineState.Idle,
            EngineState.Discovering,
            EngineState.Pairing,
            EngineState.Authenticating,
            EngineState.Negotiating,
            EngineState.Disconnected
        )
        for (state in nonEmitting) {
            engine.transitionTo(EngineState.Active)
            engine.submitButton(CanonicalButton.South, true)
            engine.submitStick(StickSide.Left, StickState(0.5f, 0.5f))
            engine.transitionTo(state)
            val post = engine.state.value
            assertTrue(
                "state $state should be neutral, got $post",
                post.buttons.isEmpty() &&
                    post.leftStick == StickState.NEUTRAL &&
                    post.rightStick == StickState.NEUTRAL &&
                    post.dpad == DpadState.Center &&
                    post.touches.isEmpty()
            )
        }
    }

    @Test
    fun section38ReconnectFirstStateIsNeutral() = runTest {
        // Step 14-15: reconnect, verify the first state is
        // neutral. The engine's contract is: a transition
        // from Disconnected → Active emits a neutral frame
        // first, so the host's first observation of the new
        // session is the disconnect-target.
        val (engine, _, _) = newEngineWithObserver()
        // Set up a session, then disconnect.
        engine.transitionTo(EngineState.Discovering)
        engine.transitionTo(EngineState.Pairing)
        engine.transitionTo(EngineState.Authenticating)
        engine.transitionTo(EngineState.Negotiating)
        engine.transitionTo(EngineState.Connected)
        engine.transitionTo(EngineState.Active)
        engine.submitButton(CanonicalButton.North, true)
        engine.transitionTo(EngineState.Disconnected)
        // Reconnect.
        engine.transitionTo(EngineState.Discovering)
        engine.transitionTo(EngineState.Pairing)
        engine.transitionTo(EngineState.Authenticating)
        engine.transitionTo(EngineState.Negotiating)
        engine.transitionTo(EngineState.Connected)
        engine.transitionTo(EngineState.Active)
        // The first state after Active is neutral.
        val post = engine.state.value
        assertTrue(
            "post-reconnect state should be neutral, got $post",
            post.buttons.isEmpty() &&
                post.leftStick == StickState.NEUTRAL &&
                post.rightStick == StickState.NEUTRAL &&
                post.dpad == DpadState.Center &&
                post.touches.isEmpty() &&
                post.motion == null
        )
    }
}
