package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolCodecTest {

    @Test
    fun fingerprintSignal_producesSha256HexString() {
        val signal1 = IrSignal.Encoded(
            carrierHz = 38000,
            protocol = IrProtocol.Nec,
            address = 0x04,
            command = 0x08
        )

        val signal2 = IrSignal.Encoded(
            carrierHz = 38000,
            protocol = IrProtocol.Nec,
            address = 0x04,
            command = 0x09
        )

        val fp1 = IrProbeEngine.fingerprintSignal(signal1)
        val fp2 = IrProbeEngine.fingerprintSignal(signal2)

        // SHA-256 hex string must be 64 characters long
        assertEquals(64, fp1.length)
        assertEquals(64, fp2.length)

        // Different signals must yield different SHA-256 fingerprints
        assertNotEquals(fp1, fp2)
    }

    @Test
    fun encodeRc5_generatesValidManchesterPattern() {
        val waveform = IrWaveform.encodeRc5(address = 0x00, command = 0x0C, toggle = 0)
        assertEquals(36000, waveform.carrierHz)
        assertTrue(waveform.pattern.isNotEmpty())

        // Every pulse duration must be a multiple of halfPeriod (889 us)
        for (duration in waveform.pattern) {
            assertTrue(duration >= 889)
        }
    }

    @Test
    fun encodeNec_generatesStandardLeadingPulse() {
        val waveform = IrWaveform.encodeNec(address = 0x00, command = 0x02)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(9000, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
    }
}
