package com.elysium.nexus.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MotionState] validation per `MASTER_ORDER.md` §14.
 *
 * Motion has no canonical range (gyro/accel/roll/pitch/yaw can
 * legitimately be large or small depending on the device). The
 * validator only rejects NaN and Infinity.
 */
class MotionStateTest {

    private fun saneMotion() = MotionState(
        gyroX = 0.01f, gyroY = -0.02f, gyroZ = 0.03f,
        accelX = 0f, accelY = 9.81f, accelZ = 0f,
        roll = 0f, pitch = 0f, yaw = 0f,
        sampleTimestampNs = 1_000_000uL
    )

    @Test
    fun realisticSampleIsValid() {
        assertTrue(MotionState.validate(saneMotion()) is ValidationResult.Valid)
    }

    @Test
    fun extremeValuesAreValid() {
        // 16 g accelerometers legitimately report ~156 m/s² on each
        // axis. The model does not gate on magnitude.
        val extreme = saneMotion().copy(accelX = 200f, accelY = -200f, gyroZ = 50f)
        assertTrue(MotionState.validate(extreme) is ValidationResult.Valid)
    }

    @Test
    fun nanInAnyFieldIsRejected() {
        val cases = listOf(
            saneMotion().copy(gyroX = Float.NaN),
            saneMotion().copy(accelY = Float.NaN),
            saneMotion().copy(roll = Float.NaN),
            saneMotion().copy(yaw = Float.NaN)
        )
        cases.forEach { state ->
            val r = MotionState.validate(state) as ValidationResult.Invalid
            assertTrue("expected NaN error for $state", r.errors.any { it is ValidationError.NaN })
        }
    }

    @Test
    fun infinityInAnyFieldIsRejected() {
        val cases = listOf(
            saneMotion().copy(gyroX = Float.POSITIVE_INFINITY),
            saneMotion().copy(accelZ = Float.NEGATIVE_INFINITY),
            saneMotion().copy(pitch = Float.POSITIVE_INFINITY)
        )
        cases.forEach { state ->
            val r = MotionState.validate(state) as ValidationResult.Invalid
            assertTrue(r.errors.any { it is ValidationError.Infinity })
        }
    }
}
