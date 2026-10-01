package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [UniversalControllerState] — the root canonical state.
 *
 * These cover the §9 invariants that survive aggregation: a neutral
 * state is the disconnect target, a valid state carries no errors,
 * and an invalid state aggregates errors from every sub-validator
 * with prefixed field paths.
 */
class UniversalControllerStateTest {

    @Test
    fun neutralStateIsValid() {
        val n = UniversalControllerState.neutral()
        assertTrue(UniversalControllerState.validate(n) is ValidationResult.Valid)
    }

    @Test
    fun neutralStateCarriesNoInput() {
        val n = UniversalControllerState.neutral(sequence = 42uL)
        assertEquals(42uL, n.sequence)
        assertTrue(n.buttons.isEmpty())
        assertEquals(DpadState.Center, n.dpad)
        assertEquals(StickState.NEUTRAL, n.leftStick)
        assertEquals(StickState.NEUTRAL, n.rightStick)
        assertEquals(TriggerState.RELEASED, n.leftTrigger)
        assertEquals(TriggerState.RELEASED, n.rightTrigger)
        assertTrue(n.touches.isEmpty())
        assertNull(n.motion)
        assertNull(n.battery)
    }

    @Test
    fun neutralWithMotionAndBatteryIsStillValid() {
        val n = UniversalControllerState.neutral().copy(
            motion = MotionState(
                gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                accelX = 0f, accelY = 9.81f, accelZ = 0f,
                roll = 0f, pitch = 0f, yaw = 0f,
                sampleTimestampNs = 100uL
            ),
            battery = BatteryState(80, true)
        )
        assertTrue(UniversalControllerState.validate(n) is ValidationResult.Valid)
    }

    @Test
    fun invalidStickIsReportedWithPrefixedPath() {
        val bad = UniversalControllerState.neutral().copy(
            leftStick = StickState(Float.NaN, 0f)
        )
        val r = UniversalControllerState.validate(bad) as ValidationResult.Invalid
        assertTrue(
            r.errors.any { it is ValidationError.NaN && it.field.startsWith("leftStick") }
        )
    }

    @Test
    fun invalidTriggerIsReportedWithPrefixedPath() {
        val bad = UniversalControllerState.neutral().copy(
            rightTrigger = TriggerState(2f)
        )
        val r = UniversalControllerState.validate(bad) as ValidationResult.Invalid
        assertTrue(
            r.errors.any {
                it is ValidationError.OutOfRange && it.field == "rightTrigger.value"
            }
        )
    }

    @Test
    fun invalidMotionIsReportedWithPrefixedPath() {
        val bad = UniversalControllerState.neutral().copy(
            motion = MotionState(
                gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                accelX = 0f, accelY = 0f, accelZ = 0f,
                roll = Float.NaN, pitch = 0f, yaw = 0f,
                sampleTimestampNs = 0uL
            )
        )
        val r = UniversalControllerState.validate(bad) as ValidationResult.Invalid
        assertTrue(
            r.errors.any { it is ValidationError.NaN && it.field.startsWith("motion") }
        )
    }

    @Test
    fun invalidBatteryIsReportedWithPrefixedPath() {
        val bad = UniversalControllerState.neutral().copy(
            battery = BatteryState(150, false)
        )
        val r = UniversalControllerState.validate(bad) as ValidationResult.Invalid
        assertTrue(
            r.errors.any {
                it is ValidationError.IntegerOutOfRange && it.field == "battery.level"
            }
        )
    }

    @Test
    fun multipleViolationsAreAllReported() {
        val bad = UniversalControllerState.neutral().copy(
            leftStick = StickState(Float.NaN, 5f),
            rightTrigger = TriggerState(-0.1f)
        )
        val r = UniversalControllerState.validate(bad) as ValidationResult.Invalid
        // NaN on leftStick.x, OutOfRange on leftStick.y, OutOfRange
        // on rightTrigger.value.
        assertTrue(r.errors.size >= 3)
    }

    @Test
    fun stateIsImmutable() {
        val a = UniversalControllerState.neutral(sequence = 1uL)
        val b = a.copy(sequence = 2uL)
        assertEquals(1uL, a.sequence)
        assertEquals(2uL, b.sequence)
        assertNotEquals(a, b)
    }

    @Test
    fun timestampAndSequenceAreIndependent() {
        val s = UniversalControllerState.neutral(sequence = 5uL, timestampNs = 10uL)
        assertEquals(5uL, s.sequence)
        assertEquals(10uL, s.timestampNs)
    }

    @Test
    fun motionAndBatteryAreOptional() {
        val s = UniversalControllerState.neutral()
        assertNull(s.motion)
        assertNull(s.battery)
        // TouchCollection is non-null but is empty by default.
        assertTrue(s.touches.isEmpty())
    }
}
