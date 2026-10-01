package com.elysium.nexus.core.engine

import com.elysium.nexus.core.filter.StickConfig
import com.elysium.nexus.core.model.BatteryState
import com.elysium.nexus.core.model.CanonicalButton
import com.elysium.nexus.core.model.DpadState
import com.elysium.nexus.core.model.MotionState
import com.elysium.nexus.core.model.StickState
import com.elysium.nexus.core.model.TouchCollection
import com.elysium.nexus.core.model.TouchPoint
import com.elysium.nexus.core.model.TriggerState
import com.elysium.nexus.core.model.ValidationError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CanonicalInputEngine] — the §9 + §12 + §32 + §38
 * producer of the canonical state.
 *
 * The engine is the most-tested class in the project. It is the
 * single source of truth for the input state, the
 * neutralization contract (§38), and the state machine (§32).
 * Every property we care about is exercised here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CanonicalInputEngineTest {

    /**
     * Build an engine with a fixed clock so we can pin
     * timestamps in tests. The clock returns a strictly
     * increasing sequence of values starting at 1000.
     */
    private fun engine(
        left: StickConfig = StickConfig(),
        right: StickConfig = StickConfig(),
        initialClock: ULong = 1000uL
    ): Pair<CanonicalInputEngine, Clock> {
        val clock = Clock(initialClock)
        val engine = CanonicalInputEngine(
            leftStickConfig = left,
            rightStickConfig = right,
            scope = TestScope(UnconfinedTestDispatcher()),
            clock = { clock.next() }
        )
        return engine to clock
    }

    private class Clock(initial: ULong) {
        private var now: ULong = initial
        fun next(): ULong {
            val v = now
            now += 1uL
            return v
        }
    }

    // -- Initial state ------------------------------------------------

    @Test
    fun initialStateIsNeutralAndIdle() = runTest {
        val (e, _) = engine()
        assertEquals(EngineState.Idle, e.engineStateValue())
        val s = e.state.value
        assertTrue("expected neutral buttons, got ${s.buttons}", s.buttons.isEmpty())
        assertEquals(DpadState.Center, s.dpad)
        assertEquals(StickState.NEUTRAL, s.leftStick)
        assertEquals(StickState.NEUTRAL, s.rightStick)
        assertEquals(TriggerState.RELEASED, s.leftTrigger)
        assertEquals(TriggerState.RELEASED, s.rightTrigger)
        assertTrue(s.touches.isEmpty())
        assertNull(s.motion)
        assertNull(s.battery)
        assertEquals(0uL, s.sequence)
        assertEquals(1000uL, s.timestampNs)
    }

    // -- Stick submission ---------------------------------------------

    @Test
    fun submitStickRequiresActive() = runTest {
        val (e, _) = engine()
        // Engine is Idle. Submission should be rejected.
        val r = e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        assertTrue("expected WrongStateMachine, got $r", r is SubmitResult.WrongStateMachine)
    }

    @Test
    fun submitStickUpdatesStateWhenActive() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        // transitionTo resets the state, which consumed a
        // sequence. The next submission is on sequence 1.
        val r = e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        assertTrue("expected Accepted, got $r", r is SubmitResult.Accepted)
        val s = (r as SubmitResult.Accepted).state
        assertTrue("expected x > 0, got $s", s.leftStick.x > 0f)
    }

    @Test
    fun submitStickRejectsNaN() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        val r = e.submitStick(StickSide.Left, StickState(Float.NaN, 0f))
        assertTrue("expected Rejected, got $r", r is SubmitResult.Rejected)
        val rej = r as SubmitResult.Rejected
        assertTrue(rej.errors.any { it is ValidationError.NaN })
    }

    @Test
    fun submitStickWithOutOfRangeInputIsFilteredToCanonical() = runTest {
        // The filter pipeline is the *normalizer* for raw touch
        // input. A stick value of magnitude 2.0 (well outside the
        // canonical [-1, 1] range) is clamped to the outer
        // threshold and saturates to magnitude 1.0. The engine
        // accepts the result, not the input.
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        val r = e.submitStick(StickSide.Right, StickState(2f, 0f))
        assertTrue("expected Accepted (filter normalizes), got $r",
            r is SubmitResult.Accepted)
        val s = e.state.value
        val mag = kotlin.math.hypot(s.rightStick.x.toDouble(), s.rightStick.y.toDouble())
        assertTrue("expected magnitude <= 1.0, got $mag", mag <= 1.0 + 1e-5)
    }

    @Test
    fun leftAndRightSticksAreIndependent() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        e.submitStick(StickSide.Right, StickState(-0.5f, 0f))
        val s = e.state.value
        assertTrue("expected left.x > 0", s.leftStick.x > 0f)
        assertTrue("expected right.x < 0", s.rightStick.x < 0f)
    }

    // -- Button submission -------------------------------------------

    @Test
    fun submitButtonUpdatesBitset() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitButton(CanonicalButton.South, true)
        e.submitButton(CanonicalButton.North, true)
        val s = e.state.value
        assertTrue(s.buttons.isPressed(CanonicalButton.South))
        assertTrue(s.buttons.isPressed(CanonicalButton.North))
        assertEquals(2, s.buttons.size())

        e.submitButton(CanonicalButton.South, false)
        val s2 = e.state.value
        assertTrue(!s2.buttons.isPressed(CanonicalButton.South))
        assertTrue(s2.buttons.isPressed(CanonicalButton.North))
    }

    @Test
    fun submitButtonRequiresActive() = runTest {
        val (e, _) = engine()
        val r = e.submitButton(CanonicalButton.South, true)
        assertTrue(r is SubmitResult.WrongStateMachine)
    }

    // -- D-pad, touches, motion, battery ------------------------------

    @Test
    fun submitDpadUpdatesState() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitDpad(DpadState.NorthEast)
        assertEquals(DpadState.NorthEast, e.state.value.dpad)
    }

    @Test
    fun submitTouchesReplacesCollection() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        val t = TouchCollection(
            listOf(TouchPoint(0, 0.5f, 0.5f, 0.5f))
        )
        e.submitTouches(t)
        assertEquals(1, e.state.value.touches.size())
        e.submitTouches(TouchCollection.EMPTY)
        assertEquals(0, e.state.value.touches.size())
    }

    @Test
    fun submitMotionUpdatesState() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        val m = MotionState(
            gyroX = 0.01f, gyroY = 0f, gyroZ = 0f,
            accelX = 0f, accelY = 9.81f, accelZ = 0f,
            roll = 0f, pitch = 0f, yaw = 0f,
            sampleTimestampNs = 1uL
        )
        e.submitMotion(m)
        assertEquals(m, e.state.value.motion)
        e.submitMotion(null)
        assertNull(e.state.value.motion)
    }

    @Test
    fun submitBatteryUpdatesState() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitBattery(BatteryState(80, true))
        assertEquals(BatteryState(80, true), e.state.value.battery)
    }

    // -- State machine -----------------------------------------------

    @Test
    fun transitionToEmitsNeutral() = runTest {
        val (e, _) = engine()
        // Drive to Active and produce some non-neutral state.
        e.transitionTo(EngineState.Active)
        e.submitButton(CanonicalButton.South, true)
        e.submitStick(StickSide.Left, StickState(0.7f, 0f))
        val sBefore = e.state.value
        assertTrue("expected non-neutral before transition, got $sBefore",
            !sBefore.buttons.isEmpty() || sBefore.leftStick.x > 0f)

        // Now transition. The state should be neutral.
        val r = e.transitionTo(EngineState.Suspended)
        assertTrue(r is TransitionResult.Transitioned)
        val sAfter = e.state.value
        assertTrue("expected neutral buttons, got ${sAfter.buttons}",
            sAfter.buttons.isEmpty())
        assertEquals(StickState.NEUTRAL, sAfter.leftStick)
        assertEquals(StickState.NEUTRAL, sAfter.rightStick)
        assertEquals(DpadState.Center, sAfter.dpad)
    }

    @Test
    fun transitionToSameStateIsNoOp() = runTest {
        val (e, _) = engine()
        val r = e.transitionTo(EngineState.Idle)
        assertTrue(r is TransitionResult.NoOp)
    }

    @Test
    fun everyTransitionEmitsNeutralPropertyTest() = runTest {
        // For every pair (from, to) of distinct states, the state
        // after the transition must be neutral. The §38 invariant.
        //
        // We do NOT force the engine through Active between
        // iterations: the engine's neutralization guarantee is a
        // property of every transition, regardless of the source
        // state. We do, however, set up a non-neutral state
        // before the transition under test (when we can), so the
        // test is meaningful — we are not just neutralising a
        // neutral state and claiming victory.
        val (e, _) = engine()
        for (from in EngineState.values()) {
            for (to in EngineState.values()) {
                if (from == to) continue
                e.transitionTo(from)
                // If the engine is now in Active, drop some
                // non-neutral input so the test is not a tautology.
                if (e.engineStateValue() == EngineState.Active) {
                    e.submitButton(CanonicalButton.North, true)
                    e.submitStick(StickSide.Right, StickState(0.5f, 0.5f))
                }
                val r = e.transitionTo(to)
                assertTrue(
                    "transition $from -> $to returned $r",
                    r is TransitionResult.Transitioned
                )
                val s = e.state.value
                assertTrue(
                    "expected neutral after $from -> $to, got $s",
                    s.buttons.isEmpty() &&
                        s.leftStick == StickState.NEUTRAL &&
                        s.rightStick == StickState.NEUTRAL &&
                        s.dpad == DpadState.Center &&
                        s.touches.isEmpty()
                )
            }
        }
    }

    @Test
    fun neutralizeWithoutTransitionResetsState() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitButton(CanonicalButton.South, true)
        e.neutralize()
        val s = e.state.value
        assertTrue(s.buttons.isEmpty())
    }

    // -- Sequence and timestamp --------------------------------------

    @Test
    fun sequenceIsMonotonicallyIncreasing() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        // Sequence starts at 0uL on the first commit; the second
        // commit is sequence 1uL; etc. We check the property
        // across the *successive* commits, not against a
        // pre-existing last.
        var prevSeq: ULong? = null
        for (i in 0..50) {
            e.submitStick(StickSide.Left, StickState(0.5f, 0f))
            val s = e.state.value
            if (prevSeq != null) {
                assertTrue(
                    "expected sequence > $prevSeq, got ${s.sequence}",
                    s.sequence > prevSeq
                )
            }
            prevSeq = s.sequence
        }
    }

    @Test
    fun timestampNeverGoesBackwards() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        var last = 0uL
        for (i in 0..50) {
            e.submitStick(StickSide.Left, StickState(0.5f, 0f))
            val s = e.state.value
            assertTrue(
                "expected timestamp > $last, got ${s.timestampNs}",
                s.timestampNs > last
            )
            last = s.timestampNs
        }
    }

    @Test
    fun monotonicTimestampSurvivesClockBackwardsJump() = runTest {
        // The clock is the injectable source. If the wall clock
        // jumps backwards, the engine must still produce a
        // strictly increasing timestamp.
        val clock = object {
            private var now: ULong = 1000uL
            fun next(): ULong { val v = now; now += 1uL; return v }
            fun set(value: ULong) { now = value }
        }
        val e = CanonicalInputEngine(
            leftStickConfig = StickConfig(),
            rightStickConfig = StickConfig(),
            scope = TestScope(UnconfinedTestDispatcher()),
            clock = { clock.next() }
        )
        e.transitionTo(EngineState.Active)
        e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        val ts1 = e.state.value.timestampNs
        clock.set(0uL) // jump backwards
        e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        val ts2 = e.state.value.timestampNs
        assertTrue("expected ts2 > ts1 even after clock jumps back, got $ts1 -> $ts2", ts2 > ts1)
    }

    @Test
    fun rejectedSubmissionDoesNotConsumeSequence() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        e.submitStick(StickSide.Left, StickState(0.5f, 0f))
        val before = e.sequence()
        e.submitStick(StickSide.Left, StickState(Float.NaN, 0f))
        val after = e.sequence()
        // The rejected submission must not bump the sequence.
        assertEquals(before, after)
    }

    @Test
    fun sequenceIsStrictlyMonotonicAcrossTransitionsAndSubmissions() = runTest {
        // Mix transitions and submissions, and verify the
        // observed sequences are strictly increasing.
        val (e, _) = engine()
        var last: ULong? = null
        fun observe() {
            val s = e.state.value
            if (last != null) {
                assertTrue(
                    "sequence regressed: $last -> ${s.sequence}",
                    s.sequence > last!!
                )
            }
            last = s.sequence
        }
        // Idle → Active transition.
        e.transitionTo(EngineState.Active); observe()
        // Submission after transition.
        e.submitStick(StickSide.Left, StickState(0.5f, 0f)); observe()
        // Active → Suspended.
        e.transitionTo(EngineState.Suspended); observe()
        // Suspended → Reconnecting.
        e.transitionTo(EngineState.Reconnecting); observe()
        // Reconnecting → Active.
        e.transitionTo(EngineState.Active); observe()
        // Submission.
        e.submitButton(CanonicalButton.South, true); observe()
        // Active → Disconnected.
        e.transitionTo(EngineState.Disconnected); observe()
    }

    // -- Composite scenarios -----------------------------------------

    @Test
    fun allButtonsCoexistInState() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        for (b in CanonicalButton.values()) {
            e.submitButton(b, true)
        }
        val s = e.state.value
        assertEquals(CanonicalButton.COUNT, s.buttons.size())
    }

    @Test
    fun submitTouchPointAddUpdateRemove() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        // Add
        e.submitTouchPoint(0, TouchPoint(0, 0.5f, 0.5f, 0.5f))
        e.submitTouchPoint(1, TouchPoint(1, 0.3f, 0.3f, 0.5f))
        assertEquals(2, e.state.value.touches.size())
        // Update
        e.submitTouchPoint(0, TouchPoint(0, 0.7f, 0.7f, 0.5f))
        val s = e.state.value
        val p0 = s.touches.points.first { it.id == 0 }
        assertEquals(0.7f, p0.x, 0f)
        assertEquals(2, s.touches.size())
        // Remove
        e.submitTouchPoint(1, null)
        assertEquals(1, e.state.value.touches.size())
    }

    @Test
    fun submitTouchPointRejectsWhenCapHit() = runTest {
        val (e, _) = engine()
        e.transitionTo(EngineState.Active)
        // Fill the cap.
        for (i in 0 until TouchCollection.MAX_TOUCHES) {
            e.submitTouchPoint(i, TouchPoint(i, 0.1f, 0.1f, 0.5f))
        }
        // One more should be rejected.
        val r = e.submitTouchPoint(
            id = 99,
            point = TouchPoint(99, 0.9f, 0.9f, 0.5f)
        )
        assertTrue("expected Rejected, got $r", r is SubmitResult.Rejected)
    }

    @Test
    fun fullSessionLifecyclePropertyTest() = runTest {
        // Drive the engine through every legal transition path
        // and verify the state is neutral at the end of every
        // transition. The §38 invariant as a single end-to-end
        // test.
        val (e, _) = engine()
        val path = listOf(
            EngineState.Discovering,
            EngineState.Pairing,
            EngineState.Authenticating,
            EngineState.Negotiating,
            EngineState.Connected,
            EngineState.Active,
            EngineState.Suspended,
            EngineState.Reconnecting,
            EngineState.Active,
            EngineState.Disconnected
        )
        for (next in path) {
            val r = e.transitionTo(next)
            assertTrue("transition to $next returned $r", r is TransitionResult.Transitioned)
            val s = e.state.value
            assertTrue(
                "expected neutral after transition to $next, got $s",
                s.buttons.isEmpty() &&
                    s.leftStick == StickState.NEUTRAL &&
                    s.rightStick == StickState.NEUTRAL &&
                    s.dpad == DpadState.Center &&
                    s.touches.isEmpty() &&
                    s.motion == null &&
                    s.battery == null
            )
        }
    }
}
