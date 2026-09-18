package com.elysium369.meet.core.obd

import com.elysium369.meet.core.obd.model.AdapterChipFamily
import com.elysium369.meet.core.obd.model.ObdAdapterMatrix
import com.elysium369.meet.ecu.adapter.EcuAdapterClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdAdapterMatrixTest {

    @Test
    fun `detects OBDLink family and STN chip accurately`() {
        val family = ObdAdapterMatrix.detectFamily(
            chipVersion = "STN1170 v4.2.1",
            deviceName = "OBDLink EX",
            stiResponse = "STN1170 v4.2.1"
        )
        assertEquals(AdapterChipFamily.OBDLINK_STN, family)

        val profile = ObdAdapterMatrix.resolveProfile(
            chipVersion = "STN1170 v4.2.1",
            deviceName = "OBDLink EX USB",
            stiResponse = "STN1170 v4.2.1"
        )
        assertEquals(AdapterChipFamily.OBDLINK_STN, profile.family)
        assertEquals("STN1170", profile.chipset)
        assertTrue("OBDLink EX supports CAN-FD and ISO-TP", profile.supportsCanFd && profile.supportsIsoTp)
        assertEquals(4096, profile.maxPacketBufferSize)
        assertEquals(EcuAdapterClassification.PROGRAMMING_CAPABLE, profile.classification)
        assertFalse(profile.isClone)
    }

    @Test
    fun `detects vLinker FD and sets high-speed CAN-FD capabilities`() {
        val family = ObdAdapterMatrix.detectFamily(
            chipVersion = "vLinker FD+ v2.2",
            deviceName = "vLinker FD+ 12345",
            stiResponse = "STN vLinker FD"
        )
        assertEquals(AdapterChipFamily.VLINKER, family)

        val profile = ObdAdapterMatrix.resolveProfile(
            chipVersion = "vLinker FD+ v2.2",
            deviceName = "vLinker FD+",
            stiResponse = "STN vLinker FD"
        )
        assertEquals(AdapterChipFamily.VLINKER, profile.family)
        assertTrue(profile.supportsCanFd)
        assertTrue(profile.supportsIsoTp)
        assertEquals(500000, profile.optimalBaudRate)
        assertEquals(20L, profile.defaultCommandDelayMs)
        assertEquals(EcuAdapterClassification.ACTIVE_DIAGNOSTIC, profile.classification)
        assertFalse(profile.isClone)
    }

    @Test
    fun `detects genuine ELM327 v2 2 and configures reliable timings`() {
        val family = ObdAdapterMatrix.detectFamily(
            chipVersion = "ELM327 v2.2",
            deviceName = "ELM327 Interface",
            stiResponse = "?"
        )
        assertEquals(AdapterChipFamily.ELM327_GENUINE, family)

        val profile = ObdAdapterMatrix.resolveProfile(
            chipVersion = "ELM327 v2.2",
            deviceName = "ELM327 Interface",
            stiResponse = "?"
        )
        assertEquals(AdapterChipFamily.ELM327_GENUINE, profile.family)
        assertFalse(profile.supportsCanFd)
        assertFalse(profile.supportsIsoTp)
        assertEquals(512, profile.maxPacketBufferSize)
        assertEquals(35L, profile.defaultCommandDelayMs)
        assertEquals(EcuAdapterClassification.DIAGNOSTIC_READ_WRITE, profile.classification)
        assertFalse(profile.isClone)
    }

    @Test
    fun `detects budget ELM327 v1 5 clone and enforces read-only safety`() {
        val family = ObdAdapterMatrix.detectFamily(
            chipVersion = "ELM327 v1.5",
            deviceName = "OBDII Bluetooth",
            stiResponse = "?"
        )
        assertEquals(AdapterChipFamily.ELM327_CLONE, family)

        val profile = ObdAdapterMatrix.resolveProfile(
            chipVersion = "ELM327 v1.5",
            deviceName = "OBDII Bluetooth",
            stiResponse = "?"
        )
        assertEquals(AdapterChipFamily.ELM327_CLONE, profile.family)
        assertTrue("ELM327 v1.5 clone is marked as clone", profile.isClone)
        assertFalse(profile.supportsCanFd)
        assertEquals(256, profile.maxPacketBufferSize)
        assertEquals(60L, profile.defaultCommandDelayMs)
        assertEquals(EcuAdapterClassification.OBD_READ_ONLY, profile.classification)
    }

    @Test
    fun `detects fake ELM327 v2 1 clone and disables adaptive timing ATAT0`() {
        val profile = ObdAdapterMatrix.resolveProfile(
            chipVersion = "ELM327 v2.1",
            deviceName = "Generic OBDII Dongle",
            stiResponse = "?"
        )
        assertEquals(AdapterChipFamily.ELM327_CLONE, profile.family)
        assertTrue(profile.isClone)
        assertTrue("Fake v2.1 requires adaptive timing disabled (ATAT0)", profile.requiresAdaptiveTimingDisabled)
        assertTrue("Init commands must include ATAT0", profile.recommendedInitCommands.contains("ATAT0"))
        assertEquals(80L, profile.defaultCommandDelayMs)
        assertEquals(64, profile.maxPacketBufferSize)
    }
}
