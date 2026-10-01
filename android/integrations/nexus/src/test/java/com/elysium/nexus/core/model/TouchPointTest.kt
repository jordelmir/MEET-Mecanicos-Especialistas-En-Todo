package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TouchPoint] validation per `MASTER_ORDER.md` §9.
 */
class TouchPointTest {

    @Test
    fun validSample() {
        assertTrue(TouchPoint.validate(TouchPoint(0, 0.5f, 0.5f, 0.5f)) is ValidationResult.Valid)
    }

    @Test
    fun cornersAreValid() {
        assertTrue(TouchPoint.validate(TouchPoint(0, 0f, 0f, 0f)) is ValidationResult.Valid)
        assertTrue(TouchPoint.validate(TouchPoint(0, 1f, 1f, 1f)) is ValidationResult.Valid)
    }

    @Test
    fun xOutOfRangeIsRejected() {
        val r = TouchPoint.validate(TouchPoint(0, 1.1f, 0f, 0f)) as ValidationResult.Invalid
        // Local field name; the outer TouchCollection validator
        // prefixes it with "points[i]." for a full path.
        assertTrue(r.errors.any { it is ValidationError.OutOfRange && it.field == "x" })
    }

    @Test
    fun negativeCoordinateIsRejected() {
        val r = TouchPoint.validate(TouchPoint(0, -0.1f, 0f, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.any { it is ValidationError.OutOfRange })
    }

    @Test
    fun nanCoordinateIsRejected() {
        val r = TouchPoint.validate(TouchPoint(0, Float.NaN, 0f, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.any { it is ValidationError.NaN })
    }

    @Test
    fun negativeIdIsRejected() {
        val r = TouchPoint.validate(TouchPoint(-1, 0f, 0f, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.any { it is ValidationError.IntegerOutOfRange })
    }

    @Test
    fun hugeIdIsRejected() {
        val r = TouchPoint.validate(TouchPoint(99999, 0f, 0f, 0f)) as ValidationResult.Invalid
        assertTrue(r.errors.any { it is ValidationError.IntegerOutOfRange })
    }

    @Test
    fun everyFieldOutOfRangeProducesMultipleErrors() {
        val r = TouchPoint.validate(TouchPoint(-1, 2f, -2f, 2f)) as ValidationResult.Invalid
        // 1 id error + 3 axis errors = 4
        assertEquals(4, r.errors.size)
    }
}
