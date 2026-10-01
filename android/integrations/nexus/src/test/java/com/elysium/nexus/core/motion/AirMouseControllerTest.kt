package com.elysium.nexus.core.motion

import com.elysium.nexus.core.model.MotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Tests for the air mouse math.
 *
 * The math is the §14 "motion cursor" feature
 * (the "tilt the phone to move the cursor"
 * feature). These tests exercise the pure
 * functions: integration, clamping, draining,
 * dead-zone, sign.
 */
class AirMouseControllerTest {

    private fun motionState(
        gyroX: Float = 0f,
        gyroY: Float = 0f,
        gyroZ: Float = 0f,
        timestampNs: ULong = 1_000_000_000UL
    ) = MotionState(
        gyroX = gyroX, gyroY = gyroY, gyroZ = gyroZ,
        accelX = 0f, accelY = 0f, accelZ = 9.81f,
        roll = 0f, pitch = 0f, yaw = 0f,
        sampleTimestampNs = timestampNs
    )

    @Test
    fun `zero rotation produces zero delta`() {
        val ctrl = AirMouseController()
        ctrl.submit(motionState(gyroY = 0f, gyroZ = 0f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroY = 0f, gyroZ = 0f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertEquals(0, d.dx)
        assertEquals(0, d.dy)
    }

    @Test
    fun `yaw rotates into X axis`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        // 1 radian / second for 1/60 sec = ~0.01667 rad.
        // At sensitivity 600, that's ~10 px.
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertTrue("dx should be positive (yaw to the right)", d.dx > 0)
        // Y should still be zero (no pitch input).
        assertEquals(0, d.dy)
    }

    @Test
    fun `pitch rotates into Y axis with invert`() {
        val ctrl = AirMouseController(sensitivity = 600f, invertY = true)
        // Positive pitch = tilting the top of
        // the phone forward (the user is
        // pointing it upward). With invertY=true
        // the cursor goes up → negative dy
        // (screen y grows downward).
        ctrl.submit(motionState(gyroY = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroY = 1f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertTrue("dy should be negative (tilt-up moves cursor up)", d.dy < 0)
    }

    @Test
    fun `pitch without invert goes the other way`() {
        val ctrl = AirMouseController(sensitivity = 600f, invertY = false)
        ctrl.submit(motionState(gyroY = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroY = 1f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertTrue("without invert, tilt-up should produce positive dy", d.dy > 0)
    }

    @Test
    fun `delta is clamped to maxDeltaPerReport`() {
        val ctrl = AirMouseController(sensitivity = 600f, maxDeltaPerReport = 127)
        // 15 rad/s for 1/60s = 0.25 rad →
        // 150 px at sensitivity 600, clamped
        // to 127.
        ctrl.submit(motionState(gyroZ = 15f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 15f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertEquals(127, d.dx)
    }

    @Test
    fun `large rotation is split across multiple reports`() {
        val ctrl = AirMouseController(sensitivity = 600f, maxDeltaPerReport = 127)
        // 30 rad/s for 1/60s = 0.5 rad → 300 px
        // at sensitivity 600, which is more than
        // 2× 127. The controller should emit two
        // 127 reports on two consecutive
        // consume() calls.
        ctrl.submit(motionState(gyroZ = 30f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 30f, timestampNs = 1_016_666_666UL))
        val first = ctrl.consume()
        val second = ctrl.consume()
        assertEquals(127, first.dx)
        assertTrue("remainder should be emitted on the next consume()", second.dx > 0)
        // The total emitted should be close to
        // the original 300 px (allowing for the
        // clamping).
        assertTrue("two consume() should have drained the rotation", first.dx + second.dx >= 200)
    }

    @Test
    fun `dead zone suppresses tiny rotations`() {
        val ctrl = AirMouseController(sensitivity = 600f, deadZoneRadians = 0.01f)
        // Inject a tiny rotation below the dead
        // zone.
        ctrl.submit(motionState(gyroZ = 0.001f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 0.001f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertEquals(0, d.dx)
    }

    @Test
    fun `reset clears the pending rotation`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_016_666_666UL))
        ctrl.reset()
        val d = ctrl.consume()
        assertEquals(0, d.dx)
        assertEquals(0, d.dy)
    }

    @Test
    fun `dt is computed from the sample timestamp`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        // Two samples 100 ms apart at 1 rad/s → 0.1 rad
        // → 60 px.
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_100_000_000UL))
        val d = ctrl.consume()
        // 0.1 rad * 600 = 60 px, clamped to ±127.
        assertEquals(60, d.dx)
    }

    @Test
    fun `large gap between samples is ignored`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        // First sample.
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        // 10 seconds later (the app was
        // backgrounded). The math should treat
        // this as a reset, not a huge dt.
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 11_000_000_000UL))
        val d = ctrl.consume()
        // The second sample's dt is > 1 second
        // so it's ignored; the first sample has
        // no previous timestamp so it's also
        // ignored. The result should be zero or
        // very small.
        assertTrue("large gap should not produce a huge delta", abs(d.dx) < 10)
    }

    @Test
    fun `negative yaw rotates cursor left`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        ctrl.submit(motionState(gyroZ = -1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = -1f, timestampNs = 1_016_666_666UL))
        val d = ctrl.consume()
        assertTrue("negative yaw should move cursor left (dx < 0)", d.dx < 0)
    }

    @Test
    fun `pending magnitude tracks the integrated rotation`() {
        val ctrl = AirMouseController(sensitivity = 600f)
        assertEquals(0f, ctrl.pendingMagnitudeRadians(), 0.0001f)
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrl.submit(motionState(gyroZ = 1f, timestampNs = 1_016_666_666UL))
        val mag = ctrl.pendingMagnitudeRadians()
        assertTrue("pending magnitude should be > 0 after a yaw sample", mag > 0f)
    }

    @Test
    fun `sensitivity scales the delta linearly`() {
        val ctrlLow = AirMouseController(sensitivity = 300f)
        val ctrlHigh = AirMouseController(sensitivity = 1200f)
        ctrlLow.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrlLow.submit(motionState(gyroZ = 1f, timestampNs = 1_016_666_666UL))
        ctrlHigh.submit(motionState(gyroZ = 1f, timestampNs = 1_000_000_000UL))
        ctrlHigh.submit(motionState(gyroZ = 1f, timestampNs = 1_016_666_666UL))
        val dLow = ctrlLow.consume()
        val dHigh = ctrlHigh.consume()
        // The high-sensitivity controller emits
        // 4× the pixels (1200/300).
        assertEquals(4, dHigh.dx / dLow.dx)
    }

    @Test
    fun `suggested sensitivity is half the screen diagonal`() {
        val s = AirMouseController.suggestedSensitivity(diagonalPx = 2000f)
        assertEquals(1000f, s, 0.001f)
    }

    @Test
    fun `radPerSecToPxPerFrame helper`() {
        val px = AirMouseController.radPerSecToPxPerFrame(radPerSec = 1f, sensitivity = 600f, hz = 60f)
        assertEquals(10f, px, 0.001f)
    }
}
