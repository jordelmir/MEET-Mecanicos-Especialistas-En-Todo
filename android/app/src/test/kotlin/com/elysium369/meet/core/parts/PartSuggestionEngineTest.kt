package com.elysium369.meet.core.parts

import com.elysium369.meet.core.parts.PartSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartSuggestionEngineTest {

    @Test
    fun `P0230 returns at least 4 distinct suggestions`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        assertTrue(s.size >= 4)
    }

    @Test
    fun `P0230 lists the relay BEFORE the fuel pump`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        val relayIndex = s.indexOfFirst { it.partName.contains("Relé", ignoreCase = true) }
        val pumpIndex = s.indexOfFirst { it.riskPart && it.partName.contains("Bomba", ignoreCase = true) }
        assertTrue("relay index $relayIndex < pump index $pumpIndex", relayIndex in 0 until pumpIndex)
    }

    @Test
    fun `P0230 fuel pump is LAST and flagged as risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        val last = s.last()
        assertTrue(last.riskPart)
        assertNotNull(last.disclaimer)
        assertTrue(last.disclaimer!!.lowercase().contains("manómetro"))
    }

    @Test
    fun `unknown DTC emits a single generic diagnostic suggestion`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P9999"))
        )
        assertEquals(1, s.size)
        assertTrue(s[0].partName.lowercase().contains("diagnóstico"))
    }

    @Test
    fun `P0420 marks the catalytic converter as risk part last`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0420"))
        )
        assertTrue(s.size >= 2)
        assertTrue(s.last().riskPart)
    }

    @Test
    fun `P0300 starts with spark plugs`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0300"))
        )
        assertTrue(s.first().partName.lowercase().contains("bujía"))
    }

    @Test
    fun `3D component fuel_pump_assembly is risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "fuel_pump_assembly")
        )
        assertEquals(1, s.size)
        assertTrue(s[0].riskPart)
    }

    @Test
    fun `3D component fuel_pump_relay is NOT risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "fuel_pump_relay")
        )
        assertEquals(1, s.size)
        assertEquals(false, s[0].riskPart)
    }

    @Test
    fun `3D component abs_module emits safety disclaimer`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "abs_module")
        )
        assertTrue(s[0].disclaimer!!.lowercase().contains("técnico"))
    }

    @Test
    fun `work order hint passes through verbatim`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.WORK_ORDER, workOrderHint = "Pastilla freno trasero")
        )
        assertEquals(1, s.size)
        assertEquals("Pastilla freno trasero", s[0].partName)
    }
}
