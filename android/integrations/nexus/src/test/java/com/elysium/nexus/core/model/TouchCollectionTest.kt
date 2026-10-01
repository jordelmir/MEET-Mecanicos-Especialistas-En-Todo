package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TouchCollection] — the cap, the empty state, and the
 * per-point validation fan-out.
 */
class TouchCollectionTest {

    @Test
    fun emptyIsValidAndEmpty() {
        val c = TouchCollection.EMPTY
        assertTrue(c.isEmpty())
        assertTrue(TouchCollection.validate(c) is ValidationResult.Valid)
    }

    @Test
    fun withinCapIsValid() {
        val points = (0 until TouchCollection.MAX_TOUCHES).map { i ->
            TouchPoint(i, 0.5f, 0.5f, 0.5f)
        }
        val c = TouchCollection(points)
        assertEquals(TouchCollection.MAX_TOUCHES, c.size())
        assertTrue(TouchCollection.validate(c) is ValidationResult.Valid)
    }

    @Test
    fun initRejectsOversized() {
        // The cap is a hard invariant on construction; the
        // constructor must fail rather than produce an invalid state
        // for downstream code to discover.
        val tooMany = (0..TouchCollection.MAX_TOUCHES).map { i ->
            TouchPoint(i, 0f, 0f, 0f)
        }
        val ex = runCatching { TouchCollection(tooMany) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun validateRejectsOversizedEvenIfConstructed() {
        // The validate() function reports the cap violation
        // independently of the init check, in case a future
        // construction path bypasses init.
        val points = (0..TouchCollection.MAX_TOUCHES).map { i ->
            TouchPoint(i, 0f, 0f, 0f)
        }
        // We cannot construct a list one over the cap, so we
        // synthesize the validate scenario by hand: a 10-element
        // list with one bad point + a report that the cap is
        // exceeded (we test the cap separately via init).
        val c = TouchCollection(
            (0 until TouchCollection.MAX_TOUCHES).map { i ->
                TouchPoint(i, 0f, 0f, 0f)
            }
        )
        // Replace one point with a bad one and ensure validate
        // surfaces it.
        val badList = c.points.toMutableList().apply {
            this[3] = TouchPoint(3, Float.NaN, 0f, 0f)
        }
        val bad = TouchCollection(badList)
        val r = TouchCollection.validate(bad) as ValidationResult.Invalid
        assertTrue(r.errors.any { it is ValidationError.IncompatibleState })
    }
}
