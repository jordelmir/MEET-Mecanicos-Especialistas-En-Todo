package com.elysium.nexus.core.motion

import com.elysium.nexus.core.model.MotionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [NullMotionSensorSource] — the
 * no-op [MotionSensorSource] used as a fallback
 * when the platform's [android.hardware.SensorManager]
 * is unavailable or the device has no IMU.
 *
 * The Android implementation
 * [AndroidMotionSensorSource] is covered by the
 * on-device end-to-end test (the emulator has
 * a virtual IMU). The interface contract — the
 * shape of the `latest()` and `samples()` API —
 * is verified here.
 */
class MotionSensorSourceTest {

    @Test
    fun nullSourceLatestIsAlwaysNull() {
        val source = NullMotionSensorSource()
        assertNull(source.latest())
    }

    @Test
    fun nullSourceSamplesIsEmpty() = runTest {
        val source = NullMotionSensorSource()
        var received = 0
        source.samples().collect { received++ }
        assertEquals(0, received)
    }

    @Test
    fun nullSourceRecenterIsNoOp() {
        val source = NullMotionSensorSource()
        source.recenter() // no-op; should not throw
        assertNull(source.latest())
    }

    @Test
    fun nullSourceCloseIsIdempotent() {
        val source = NullMotionSensorSource()
        source.close()
        source.close() // second close is also a no-op
        assertNull(source.latest())
    }
}

/**
 * A fake source for unit tests. Emits a deterministic
 * sequence of samples on demand. The fake implements
 * the [MotionSensorSource] interface as a plain class
 * (not a `Mockito` mock); the interface is small
 * enough that a hand-rolled fake is more readable
 * than a generated one.
 */
class FakeMotionSensorSource : MotionSensorSource {
    private val samples: MutableList<MotionState> = mutableListOf()
    private var recentered: Boolean = false

    fun emit(sample: MotionState) {
        samples.add(sample)
    }

    override fun samples() = kotlinx.coroutines.flow.flow {
        for (sample in samples) emit(sample)
    }

    override fun latest(): MotionState? = samples.lastOrNull()

    override fun recenter() {
        recentered = true
    }

    fun isRecentered(): Boolean = recentered

    override fun close() {
        samples.clear()
    }
}

/**
 * Tests for [FakeMotionSensorSource]. The fake is
 * used by the activity's tests (Phase 1.5+) to
 * verify the motion pipeline end-to-end.
 */
class FakeMotionSensorSourceTest {

    @Test
    fun fakeEmitsInOrder() = runTest {
        val source = FakeMotionSensorSource()
        source.emit(makeMotion(0f, 0f, 0f))
        source.emit(makeMotion(0.1f, 0.1f, 0.1f))
        source.emit(makeMotion(0.2f, 0.2f, 0.2f))
        val received = mutableListOf<MotionState>()
        source.samples().collect { received.add(it) }
        assertEquals(3, received.size)
        assertEquals(0f, received[0].gyroX, 1e-6f)
        assertEquals(0.2f, received[2].gyroX, 1e-6f)
    }

    @Test
    fun fakeLatestReturnsLastEmitted() {
        val source = FakeMotionSensorSource()
        source.emit(makeMotion(0f, 0f, 0f))
        source.emit(makeMotion(1f, 2f, 3f))
        val latest = source.latest()
        assertNotNull(latest)
        assertEquals(1f, latest!!.gyroX, 1e-6f)
        assertEquals(2f, latest.gyroY, 1e-6f)
        assertEquals(3f, latest.gyroZ, 1e-6f)
    }

    @Test
    fun fakeRecenterIsObservable() {
        val source = FakeMotionSensorSource()
        assertEquals(false, source.isRecentered())
        source.recenter()
        assertEquals(true, source.isRecentered())
    }

    @Test
    fun fakeCloseClearsSamples() {
        val source = FakeMotionSensorSource()
        source.emit(makeMotion(0f, 0f, 0f))
        source.close()
        assertNull(source.latest())
    }

    private fun makeMotion(x: Float, y: Float, z: Float): MotionState = MotionState(
        gyroX = x, gyroY = y, gyroZ = z,
        accelX = 0f, accelY = 0f, accelZ = 0f,
        roll = 0f, pitch = 0f, yaw = 0f,
        sampleTimestampNs = 0uL
    )
}
