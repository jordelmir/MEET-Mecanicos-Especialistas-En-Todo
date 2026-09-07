package com.elysium369.meet.education.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcCurriculumBridgeTest {

    @Test
    fun `resolves canonical DTC P0230 to 9th grade Ohm law and electrical sandbox`() {
        val bridge = DtcCurriculumBridge.getBridgeForDtc("P0230")
        assertNotNull("P0230 must be registered in bridge", bridge)
        assertEquals("P0230", bridge?.dtcCode)
        assertEquals("cr_elec9_c_ley_ohm", bridge?.primaryConceptId)
        assertEquals(9, bridge?.academicGrade)
        assertEquals("ELECTRICAL", bridge?.sandboxType)
        assertTrue(bridge?.underlyingPhysicsPrinciple?.contains("Ley de Ohm") == true || bridge?.underlyingPhysicsPrinciple?.contains("caída de tensión") == true)
    }

    @Test
    fun `resolves canonical DTC P0562 system low voltage`() {
        val bridge = DtcCurriculumBridge.getBridgeForDtc("p0562") // case insensitive
        assertNotNull(bridge)
        assertEquals("P0562", bridge?.dtcCode)
        assertEquals("cr_elec9_c_bateria_alternador", bridge?.primaryConceptId)
    }

    @Test
    fun `finds bridges for multiple scanner DTCs`() {
        val dtcs = listOf("P0230", "P0115", "UNKNOWN_999")
        val found = DtcCurriculumBridge.findBridgesForActiveDtcs(dtcs)
        assertEquals(2, found.size)
        assertTrue(found.any { it.dtcCode == "P0230" })
        assertTrue(found.any { it.dtcCode == "P0115" })
    }

    @Test
    fun `unregistered DTC returns null safely`() {
        val bridge = DtcCurriculumBridge.getBridgeForDtc("P9999")
        assertNull(bridge)
    }
}
