package com.elysium.nexus.core.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TriggerConfig] — the per-trigger knob set.
 */
class TriggerConfigTest {

    @Test
    fun defaultsAreSane() {
        val c = TriggerConfig()
        assertEquals(0.05f, c.deadzone, 0f)
        assertEquals(0.10f, c.activationPoint, 0f)
        assertEquals(false, c.hairTrigger)
        assertEquals(ResponseCurve.Linear, c.responseCurve)
        assertEquals(ResponseCurve.Linear, c.returnCurve)
        assertEquals(null, c.reducedRange)
    }

    @Test
    fun deadzoneMustBeInRange() {
        try {
            TriggerConfig(deadzone = -0.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            TriggerConfig(deadzone = 1.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun activationPointMustBeAtLeastDeadzone() {
        // Below the deadzone is rejected; at or above is fine.
        try {
            TriggerConfig(deadzone = 0.30f, activationPoint = 0.10f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        // equal is allowed (the trigger goes digital the
        // moment the analog value exits the deadzone).
        assertNotNull(TriggerConfig(deadzone = 0.30f, activationPoint = 0.30f))
    }

    @Test
    fun reducedRangeMustBeAValidSubrange() {
        try {
            TriggerConfig(reducedRange = 0.7f..0.3f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            TriggerConfig(reducedRange = -0.1f..0.5f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
        try {
            TriggerConfig(reducedRange = 0.5f..1.1f)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun hairTriggerConfigIsAccepted() {
        val c = TriggerConfig(hairTrigger = true)
        assertTrue(c.hairTrigger)
    }
}
