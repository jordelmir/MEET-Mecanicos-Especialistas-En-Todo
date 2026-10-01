package com.elysium.nexus.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [AcState] serialization.
 */
class AcStateTest {

    @Test
    fun serializeProducesExpectedFormat() {
        val state = AcState(temperature = 22, mode = 1, fanSpeed = 2, powerOn = true)
        val serialized = state.serialize()
        assertEquals("t=22|m=1|f=2|p=1", serialized)
    }

    @Test
    fun serializeWithPowerOff() {
        val state = AcState(temperature = 24, mode = 0, fanSpeed = 0, powerOn = false)
        val serialized = state.serialize()
        assertEquals("t=24|m=0|f=0|p=0", serialized)
    }

    @Test
    fun parseRoundTrips() {
        val original = AcState(temperature = 20, mode = 4, fanSpeed = 3, powerOn = true)
        val parsed = AcState.parse(original.serialize())
        assertNotNull(parsed)
        assertEquals(original, parsed)
    }

    @Test
    fun parseReturnsDefaultForGarbage() {
        // Garbage input produces defaults (reasonable fallback for UI)
        val parsed = AcState.parse("not-a-valid-state")
        assertNotNull(parsed)
        assertEquals(24, parsed!!.temperature)
        assertEquals(1, parsed.mode)
        assertEquals(0, parsed.fanSpeed)
        assertTrue(parsed.powerOn)
    }

    @Test
    fun parseHandlesPartialData() {
        val parsed = AcState.parse("t=18|m=2")
        assertNotNull(parsed)
        assertEquals(18, parsed!!.temperature)
        assertEquals(2, parsed.mode)
        // fanSpeed and powerOn use defaults
        assertEquals(0, parsed.fanSpeed)
        assertTrue(parsed.powerOn)
    }

    @Test
    fun defaultState() {
        val state = AcState()
        assertEquals(24, state.temperature)
        assertEquals(1, state.mode)
        assertEquals(0, state.fanSpeed)
        assertTrue(state.powerOn)
    }
}
