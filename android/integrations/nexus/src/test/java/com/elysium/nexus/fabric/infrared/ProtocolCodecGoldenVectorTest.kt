package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

import com.elysium.nexus.core.device.IrSignal

class ProtocolCodecGoldenVectorTest {

    @Test
    fun necGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeNec(address = 0x00, command = 0x07)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(9000, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun necExtendedGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeNecExtended(address = 0x0400, command = 0x07)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(9000, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun samsungGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeSamsung(address = 0x07, command = 0x02)
        assertEquals(38000, waveform.carrierHz)
        assertEquals(4500, waveform.pattern[0])
        assertEquals(4500, waveform.pattern[1])
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun sonySircGoldenVector_producesDeterministicTimings() {
        val waveform = IrWaveform.encodeSonySirc(address = 0x01, command = 0x12)
        assertEquals(40000, waveform.carrierHz)
        assertEquals(2400, waveform.pattern[0])
        assertEquals(600, waveform.pattern[1])
    }

    @Test
    fun aiwaGoldenVector_producesDeterministicTimings() {
        // Golden vector from IrpProtocols.xml Aiwa definition with the
        // probonopd/irdb Konka remote values (device=25, subdevice=1).
        val waveform = IrWaveform.encodeAiwa(address = 25, subDevice = 1, command = 0x05)
        assertEquals(38123, waveform.carrierHz)
        assertEquals(8800, waveform.pattern[0])
        assertEquals(4400, waveform.pattern[1])
        assertEquals(87, waveform.pattern.size)
        assertEquals(550, waveform.pattern[2])
        assertEquals(1650, waveform.pattern[3])
    }

    @Test
    fun aiwaEncodedSignal_dispatchesThroughIrProtocolEncode() {
        val signal = IrSignal.Encoded(
            carrierHz = 38123,
            protocol = IrProtocol.Aiwa,
            address = 25,
            subDevice = 1,
            command = 0x05,
            codecId = "AIWA",
            variantId = "AIWA_42"
        )
        val direct = IrWaveform.encodeAiwa(address = 25, subDevice = 1, command = 0x05)
        when (val result = IrProtocol.encode(signal)) {
            is EncodeResult.Success -> assertTrue(
                "Aiwa dispatch must equal direct encoder output",
                result.waveform.pattern.contentEquals(direct.pattern)
            )
            else -> fail("Aiwa dispatch must succeed, got $result")
        }
    }

    @Test
    fun rc5DifferentCommands_produceDifferentWaveforms() {
        val volumeUp = IrWaveform.encodeRc5(address = 0, command = 16, toggle = 0)
        val volumeDown = IrWaveform.encodeRc5(address = 0, command = 17, toggle = 0)

        assertFalse(
            "Different RC5 commands MUST produce different physical waveforms!",
            volumeUp.pattern.contentEquals(volumeDown.pattern)
        )
    }

    @Test
    fun protocolCodecRegistry_verifiesCodecsCorrectly() {
        val necSpec = ProtocolCodecRegistry.getCodec("NEC")
        assertNotNull(necSpec)
        // Honest state: unit-shape validated until decoder round-trip + HIL proof exist (dictamen P1).
        assertEquals(CodecVerificationStatus.UNIT_SHAPE_VALIDATED, necSpec?.status)
        assertTrue(ProtocolCodecRegistry.isCodecTransmittable("NEC"))
        // P0-7: EXPERIMENTAL codecs (RC5, RC6, Kaseikyo) are blocked in production
        assertFalse(ProtocolCodecRegistry.isCodecTransmittable("RC5"))
        assertFalse(ProtocolCodecRegistry.isCodecTransmittable("RC6"))
        assertFalse(ProtocolCodecRegistry.isCodecTransmittable("KASEIKYO"))
    }

    @Test
    fun aiwaCodec_registersAndResolvesVariant() {
        val spec = ProtocolCodecRegistry.getCodec("AIWA")
        assertNotNull(spec)
        assertEquals(IrProtocol.Aiwa, spec?.protocol)
        assertTrue(ProtocolCodecRegistry.isCodecTransmittable("AIWA"))
        val resolved = ProtocolCodecRegistry.resolve("Aiwa", "AIWA_42")
        assertTrue("AIWA with AIWA_42 hint must resolve", resolved is CodecResolution.Resolved)
        assertEquals("AIWA_42", (resolved as CodecResolution.Resolved).variant?.variantId)
        val unsupported = ProtocolCodecRegistry.resolve("Aiwa", "AIWA_48")
        assertTrue("Unknown Aiwa variant must fail closed", unsupported is CodecResolution.VariantUnsupported)
    }
}
