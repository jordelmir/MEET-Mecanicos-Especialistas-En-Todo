package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StickState] range validation per `MASTER_ORDER.md` §9.
 */
class StickStateTest {

    @Test
    fun neutralIsZeroZero() {
        assertEquals(0f, StickState.NEUTRAL.x, 0f)
        assertEquals(0f, StickState.NEUTRAL.y, 0f)
    }

    @Test
    fun validSamplesAtCorners() {
        assertTrue(StickState.validate(StickState(1f, 1f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(-1f, -1f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(1f, -1f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(-1f, 1f)) is ValidationResult.Valid)
    }

    @Test
    fun validSamplesAtAxes() {
        assertTrue(StickState.validate(StickState(1f, 0f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(0f, 1f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(-1f, 0f)) is ValidationResult.Valid)
        assertTrue(StickState.validate(StickState(0f, -1f)) is ValidationResult.Valid)
    }

    @Test
    fun outOfRangeIsRejected() {
        val r = StickState.validate(StickState(1.5f, 0f)) as ValidationResult.Invalid
        assertEquals(1, r.errors.size)
        val err = r.errors.single() as ValidationError.OutOfRange
        // The inner validator emits local field names ("x", "y").
        // The outer UniversalControllerState validator prefixes them
        // with the field path of the parent ("leftStick.x", etc.).
        assertEquals("x", err.field)
        assertEquals(1.5f, err.value, 0f)
    }

    @Test
    fun nanIsRejected() {
        val r = StickState.validate(StickState(Float.NaN, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.NaN)
    }

    @Test
    fun infinityIsRejected() {
        val r = StickState.validate(StickState(Float.POSITIVE_INFINITY, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.Infinity)
        val r2 = StickState.validate(StickState(0f, Float.NEGATIVE_INFINITY)) as ValidationResult.Invalid
        assertTrue(r2.errors.single() is ValidationError.Infinity)
    }

    @Test
    fun bothAxesOutOfRangeProducesTwoErrors() {
        val r = StickState.validate(StickState(2f, -2f)) as ValidationResult.Invalid
        assertEquals(2, r.errors.size)
    }
}
