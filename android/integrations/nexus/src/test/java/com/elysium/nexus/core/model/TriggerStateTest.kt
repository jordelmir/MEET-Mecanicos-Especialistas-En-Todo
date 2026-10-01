package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TriggerState] range validation per `MASTER_ORDER.md` §9.
 */
class TriggerStateTest {

    @Test
    fun releasedIsZero() {
        assertEquals(0f, TriggerState.RELEASED.value, 0f)
    }

    @Test
    fun fullIsOne() {
        assertEquals(1f, TriggerState.FULL.value, 0f)
    }

    @Test
    fun midpointIsValid() {
        assertTrue(TriggerState.validate(TriggerState(0.5f)) is ValidationResult.Valid)
    }

    @Test
    fun negativeIsRejected() {
        val r = TriggerState.validate(TriggerState(-0.01f)) as ValidationResult.Invalid
        assertEquals(1, r.errors.size)
        val err = r.errors.single() as ValidationError.OutOfRange
        assertEquals(0f, err.min, 0f)
        assertEquals(1f, err.max, 0f)
    }

    @Test
    fun aboveOneIsRejected() {
        val r = TriggerState.validate(TriggerState(1.01f)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.OutOfRange)
    }

    @Test
    fun nanIsRejected() {
        val r = TriggerState.validate(TriggerState(Float.NaN)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.NaN)
    }

    @Test
    fun infinityIsRejected() {
        val r = TriggerState.validate(TriggerState(Float.POSITIVE_INFINITY)) as ValidationResult.Invalid
        assertTrue(r.errors.single() is ValidationError.Infinity)
    }
}
