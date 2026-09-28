package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolTest {

    @Test
    fun `encode dispatches NEC correctly`() {
        val signal = IrSignal.Encoded(
            carrierHz = 38_000,
            protocol = IrProtocol.Nec,
            address = 0x00,
            command = 0x02
        )
        val result = IrProtocol.encode(signal)
        assertTrue(result is EncodeResult.Success)
        val waveform = (result as EncodeResult.Success).waveform
        assertEquals(38_000, waveform.carrierHz)
        assertEquals(67, waveform.pattern.size)
        assertTrue(waveform.pattern.all { it > 0 })
    }

    @Test
    fun `encode dispatches Samsung correctly`() {
        val signal = IrSignal.Encoded(
            carrierHz = 38_000,
            protocol = IrProtocol.Samsung,
            address = 0x07,
            command = 0x02
        )
        val result = IrProtocol.encode(signal)
        assertTrue(result is EncodeResult.Success)
        val waveform = (result as EncodeResult.Success).waveform
        assertEquals(38_000, waveform.carrierHz)
        assertEquals(67, waveform.pattern.size)
    }

    @Test
    fun `encode handles raw waveform payloads`() {
        val rawPattern = intArrayOf(9000, 4500, 560, 560, 560)
        val signal = IrSignal.Raw(carrierHz = 38_000, patternUs = rawPattern)
        val result = IrProtocol.encode(signal)
        assertTrue(result is EncodeResult.Success)
        val waveform = (result as EncodeResult.Success).waveform
        assertEquals(38_000, waveform.carrierHz)
        assertEquals(5, waveform.pattern.size)
    }
}
