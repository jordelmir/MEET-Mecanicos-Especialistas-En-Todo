package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.TriggerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TriggerDigitalDetector] — the analog → digital
 * transition detector.
 */
class TriggerDigitalDetectorTest {

    @Test
    fun belowActivationPointIsNotDigital() {
        val d = TriggerDigitalDetector(TriggerConfig(activationPoint = 0.10f))
        assertFalse(d.update(TriggerState(0.05f)))
        assertFalse(d.update(TriggerState(0.099f)))
    }

    @Test
    fun atActivationPointIsDigital() {
        val d = TriggerDigitalDetector(TriggerConfig(activationPoint = 0.10f))
        assertTrue(d.update(TriggerState(0.10f)))
    }

    @Test
    fun aboveActivationPointIsDigital() {
        val d = TriggerDigitalDetector(TriggerConfig(activationPoint = 0.10f))
        assertTrue(d.update(TriggerState(0.50f)))
        assertTrue(d.update(TriggerState(1.0f)))
    }

    @Test
    fun hairTriggerActivatesAtAnyNonZeroValue() {
        val d = TriggerDigitalDetector(
            TriggerConfig(activationPoint = 0.50f, hairTrigger = true)
        )
        // Any non-zero value is digital in hair-trigger mode,
        // regardless of the activationPoint.
        assertTrue(d.update(TriggerState(0.01f)))
        assertTrue(d.update(TriggerState(0.30f)))
        assertFalse(d.update(TriggerState(0.0f)))
    }

    @Test
    fun resetReturnsToFalse() {
        val d = TriggerDigitalDetector(TriggerConfig())
        d.update(TriggerState(1.0f))
        assertTrue(d.lastDigital())
        d.reset()
        assertFalse(d.lastDigital())
    }

    @Test
    fun lastDigitalTracksTheLatestUpdate() {
        val d = TriggerDigitalDetector(TriggerConfig(activationPoint = 0.10f))
        d.update(TriggerState(0.05f))
        assertFalse(d.lastDigital())
        d.update(TriggerState(0.20f))
        assertTrue(d.lastDigital())
        d.update(TriggerState(0.05f))
        assertFalse(d.lastDigital())
    }
}
