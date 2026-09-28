package com.elysium.nexus.core.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StickConfig] — the per-stick knob set. The contract
 * is "every invalid config is rejected at construction", so the
 * filter function itself never has to validate.
 */
class StickConfigTest {

    @Test
    fun defaultsAreSane() {
        val c = StickConfig()
        assertEquals(0.10f, c.innerDeadzone, 0f)
        assertEquals(0.95f, c.outerThreshold, 0f)
        assertEquals(0f, c.antiDeadzone, 0f)
        assertEquals(ResponseCurve.Linear, c.responseCurve)
        assertEquals(1f, c.sensitivity, 0f)
        assertEquals(false, c.invertX)
        assertEquals(false, c.invertY)
        assertEquals(0f, c.snapToCardinal, 0f)
        assertEquals(1f, c.saturation, 0f)
        assertEquals(null, c.reducedRange)
        assertEquals(StickMode.FixedCenter, c.mode)
    }

    @Test
    fun innerDeadzoneMustBeInRange() {
        try {
            StickConfig(innerDeadzone = -0.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            StickConfig(innerDeadzone = 1.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun outerThresholdMustBeGreaterThanInner() {
        try {
            StickConfig(innerDeadzone = 0.5f, outerThreshold = 0.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            StickConfig(innerDeadzone = 0.6f, outerThreshold = 0.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun sensitivityMustBeNonNegative() {
        try {
            StickConfig(sensitivity = -0.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        // Zero is allowed (degenerate but not a bug).
        assertNotNull(StickConfig(sensitivity = 0f))
    }

    @Test
    fun reducedRangeMustBeAValidSubrange() {
        // start > endInclusive is rejected.
        try {
            StickConfig(reducedRange = 0.7f..0.3f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        // out-of-bounds is rejected.
        try {
            StickConfig(reducedRange = -0.1f..0.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            StickConfig(reducedRange = 0.5f..1.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun validConfigsAreAccepted() {
        // A few configurations that should be accepted.
        StickConfig(innerDeadzone = 0.05f, outerThreshold = 0.9f)
        StickConfig(antiDeadzone = 0.1f)
        StickConfig(responseCurve = ResponseCurve.Exponential(2f))
        StickConfig(reducedRange = 0.2f..0.6f)
        StickConfig(mode = StickMode.Precision)
        // We can construct any mode in 0.3; the pipeline just
        // falls through to FixedCenter until 0.4 lands.
        for (mode in StickMode.values()) {
            StickConfig(mode = mode)
        }
    }
}
