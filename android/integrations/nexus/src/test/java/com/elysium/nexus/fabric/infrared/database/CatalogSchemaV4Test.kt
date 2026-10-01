package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.ProtocolResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit test for Schema v4 multi-command Code Sets and strict protocol resolution.
 */
class CatalogSchemaV4Test {

    @Test
    fun testCodeSetContainsMultipleCommands() {
        val volumeUp = IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x07)
        val volumeDown = IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x06)
        val mute = IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x08)
        val power = IrSignal.Encoded(38000, IrProtocol.Nec, 0x00, null, 0x02)

        val codeSet = IrCodeSet(
            id = "cs-sankey-tv-01",
            brand = "Sankey",
            modelPatterns = setOf("CLED-32"),
            remoteModels = setOf("RC-32"),
            commands = mapOf(
                IrAction.VOLUME_UP to volumeUp,
                IrAction.VOLUME_DOWN to volumeDown,
                IrAction.MUTE to mute,
                IrAction.POWER_TOGGLE to power
            ),
            provenance = com.elysium.nexus.core.device.CodeProvenance("Flipper", "", "CC0-1.0"),
            verification = VerificationStatus.UNVERIFIED
        )

        assertEquals("cs-sankey-tv-01", codeSet.id)
        assertEquals(4, codeSet.commands.size)
        assertNotNull(codeSet.commands[IrAction.VOLUME_UP])
        assertNotNull(codeSet.commands[IrAction.VOLUME_DOWN])
        assertNotNull(codeSet.commands[IrAction.MUTE])
        assertNotNull(codeSet.commands[IrAction.POWER_TOGGLE])
    }

    @Test
    fun testStrictProtocolResolution() {
        // NECx must resolve to NecExtended, NOT Nec!
        val necxRes = IrProtocol.resolveProtocol("NECx")
        assertTrue(necxRes is ProtocolResolution.Supported)
        assertEquals(IrProtocol.NecExtended, (necxRes as ProtocolResolution.Supported).protocol)

        // NEC resolves to Nec
        val necRes = IrProtocol.resolveProtocol("NEC")
        assertTrue(necRes is ProtocolResolution.Supported)
        assertEquals(IrProtocol.Nec, (necRes as ProtocolResolution.Supported).protocol)

        // Unknown protocol must return Unsupported, zero fallback to NEC
        val unknownRes = IrProtocol.resolveProtocol("FOO_BAR_UNKNOWN_123")
        assertTrue(unknownRes is ProtocolResolution.Unsupported)
        assertEquals("FOO_BAR_UNKNOWN_123", (unknownRes as ProtocolResolution.Unsupported).originalName)
    }
}
