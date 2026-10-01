package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real learning path unit tests: the wire format that the
 * Nexus Receiver / agent sends over Wi-Fi or USB-C is parsed
 * by [IrCaptureBridge.parseFrame] and decoded by [IrLearner]
 * end to end.
 */
class IrCaptureBridgeTest {

    private fun necFrame(): String {
        // Golden NEC: address 0x07, command 0x45, carrier 38000 Hz.
        // Built with the same encoder the runtime uses, so the test
        // proves encode → wire → parse → decode round-trips.
        val w = IrWaveform.encodeNec(address = 0x07, command = 0x45)
        val pattern = w.pattern.joinToString(",")
        return """{"carrierHz":38000,"pattern":[$pattern]}"""
    }

    @Test
    fun parseFrame_acceptsNexusReceiverJson() {
        val frame = IrCaptureBridge.parseFrame(necFrame())
        assertNotNull(frame)
        assertEquals(38_000, frame!!.carrierHz)
        assertTrue(frame.pattern.size >= 65)
    }

    @Test
    fun parseFrame_rejectsGarbage() {
        assertNull(IrCaptureBridge.parseFrame("hello"))
        assertNull(IrCaptureBridge.parseFrame("""{"carrierHz":0,"pattern":[9000]}"""))
        assertNull(IrCaptureBridge.parseFrame("""{"carrierHz":38000,"pattern":[]}"""))
        assertNull(IrCaptureBridge.parseFrame(""))
    }

    @Test
    fun parseFrame_acceptsLegacyCarrierKey() {
        val frame = IrCaptureBridge.parseFrame(
            """{"carrier":38000,"pattern":[9000,4500,560,1690]}"""
        )
        assertNotNull(frame)
        assertEquals(38_000, frame!!.carrierHz)
        assertEquals(4, frame.pattern.size)
    }

    @Test
    fun learnedNecWaveformRoundTrips() {
        val frame = IrCaptureBridge.parseFrame(necFrame())!!
        val result = IrLearner.learn(frame.pattern, sampleRateHz = 1_000_000)
        assertNotNull(result.command)
        assertEquals(IrProtocol.Nec, result.command!!.protocol)
        assertEquals(0x07, result.command!!.address)
        assertEquals(0x45, result.command!!.command)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun learnedNecExtendedWaveformDecodes() {
        val w = IrWaveform.encodeNecExtended(address = 0x10, command = 0x08)
        val frame = IrCaptureBridge.parseFrame(
            """{"carrierHz":38000,"pattern":[${w.pattern.joinToString(",")}]}"""
        )!!
        val result = IrLearner.learn(frame.pattern, sampleRateHz = 1_000_000)
        assertNotNull(result.command)
        assertEquals(IrProtocol.NecExtended, result.command!!.protocol)
        assertEquals(0x10, result.command!!.address)
        assertEquals(0x08, result.command!!.command)
    }
}