package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcEngineTest {

    private val engine = DtcEngine()

    @Test
    fun `normalizes lowercase`() {
        val r = engine.normalize("p0230")
        assertTrue(r is DtcEngine.NormalizeResult.Valid)
        r as DtcEngine.NormalizeResult.Valid
        assertEquals("P0230", r.code)
        assertNull(r.failureTypeHex)
    }

    @Test
    fun `normalizes dash failure type`() {
        val r = engine.normalize("P0230-13")
        assertTrue(r is DtcEngine.NormalizeResult.Valid)
        r as DtcEngine.NormalizeResult.Valid
        assertEquals("P0230", r.code)
        assertEquals("13", r.failureTypeHex)
    }

    @Test
    fun `normalizes colon failure type`() {
        val r = engine.normalize("P0230:13")
        assertTrue(r is DtcEngine.NormalizeResult.Valid)
        r as DtcEngine.NormalizeResult.Valid
        assertEquals("P0230", r.code)
        assertEquals("13", r.failureTypeHex)
    }

    @Test
    fun `normalizes U-code`() {
        val r = engine.normalize("u0100")
        assertTrue(r is DtcEngine.NormalizeResult.Valid)
        r as DtcEngine.NormalizeResult.Valid
        assertEquals("U0100", r.code)
    }

    @Test
    fun `rejects garbage`() {
        val r = engine.normalize("basura")
        assertTrue(r is DtcEngine.NormalizeResult.Invalid)
    }

    @Test
    fun `rejects second digit out of range`() {
        // P9999: second char is 9, but spec allows only 0-3.
        val r = engine.normalize("P9999")
        assertTrue(r is DtcEngine.NormalizeResult.Invalid)
    }

    @Test
    fun `rejects wrong prefix`() {
        val r = engine.normalize("X0230")
        assertTrue(r is DtcEngine.NormalizeResult.Invalid)
    }

    @Test
    fun `rejects empty string`() {
        val r = engine.normalize("")
        assertTrue(r is DtcEngine.NormalizeResult.Invalid)
    }

    @Test
    fun `rejects malformed failure type`() {
        val r = engine.normalize("P0230-XX")
        assertTrue(r is DtcEngine.NormalizeResult.Invalid)
    }

    @Test
    fun `engine with profile returns it`() {
        val profile = DtcProfile(
            code = "P0230",
            system = "Fuel System",
            severity = "HIGH",
            description = "Fuel Pump Primary Circuit"
        )
        val e = DtcEngine(mapOf("P0230" to profile))
        val r = e.getDtcProfile("p0230")
        assertNotNull(r)
        assertEquals("P0230", r?.code)
        assertEquals("Fuel System", r?.system)
    }

    @Test
    fun `engine without profile returns null`() {
        assertNull(engine.getDtcProfile("P0230"))
        assertNull(engine.getDtcProfile("Z9999"))
    }

    @Test
    fun `related systems empty by default`() {
        val r = engine.getRelatedSystems("P0230")
        assertTrue(r.isEmpty())
    }

    @Test
    fun `isValid shortcut`() {
        assertTrue(engine.isValid("P0230"))
        assertFalse(engine.isValid("P9999"))
    }
}
