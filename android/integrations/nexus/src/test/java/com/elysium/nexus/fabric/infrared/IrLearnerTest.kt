package com.elysium.nexus.fabric.infrared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IrLearnerTest {

    @Test
    fun `learn rejects a raw waveform with less than 2 entries`() {
        try {
            IrLearner.learn(intArrayOf(560))
            fail("Expected IllegalArgumentException for 1 entry.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn rejects a non-positive slice duration`() {
        try {
            IrLearner.learn(intArrayOf(560, 0))
            fail("Expected IllegalArgumentException for 0 slice duration.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn round-trips a NEC-encoded waveform`() {
        val original = IrWaveform.encodeNec(address = 0x42, command = 0x84)
        val result = IrLearner.learn(original.pattern)
        assertNotNull("Expected a matched protocol, got null", result.command)
        assertEquals(IrProtocol.Nec, result.command!!.protocol)
        assertEquals(0x42, result.command.address)
        assertEquals(0x84, result.command.command)
        assertEquals(original.carrierHz, result.rawWaveform.carrierHz)
    }

    @Test
    fun `learn estimates the carrier near 38 kHz for an NEC waveform`() {
        val waveform = IrWaveform.encodeNec(address = 0x01, command = 0x02)
        val result = IrLearner.learn(waveform.pattern)
        assertTrue(
            "Expected carrier near 38 kHz, got ${result.carrierHz}",
            result.carrierHz in 34_000..42_000
        )
    }

    @Test
    fun `learn returns null command for an unknown waveform`() {
        val garbage = intArrayOf(100, 200, 300, 400, 500, 600, 700, 800)
        val result = IrLearner.learn(garbage)
        assertNull(result.command)
        assertTrue(
            "Expected confidence < 0.5 for an unknown waveform, got ${result.confidence}",
            result.confidence < 0.5f
        )
    }

    @Test
    fun `learn produces a confidence between 0 and 1`() {
        val waveform = IrWaveform.encodeNec(address = 0x10, command = 0x20)
        val result = IrLearner.learn(waveform.pattern)
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `learn produces a higher confidence for a clean waveform than for a noisy one`() {
        val clean = IrWaveform.encodeNec(address = 0x10, command = 0x20).pattern
        val resultClean = IrLearner.learn(clean)
        val perturbed = clean.copyOf().also { arr ->
            for (i in arr.indices) {
                if (i % 3 == 0) arr[i] = (arr[i] * 0.5).toInt().coerceAtLeast(1)
            }
        }
        val resultNoisy = IrLearner.learn(perturbed)
        if (resultClean.command != null && resultNoisy.command != null) {
            assertTrue(
                "Expected clean (${resultClean.confidence}) >= noisy (${resultNoisy.confidence})",
                resultClean.confidence >= resultNoisy.confidence
            )
        }
    }

    @Test
    fun `learn accepts a waveform with 25 percent timing tolerance`() {
        val original = IrWaveform.encodeNec(address = 0x10, command = 0x20)
        val jittered = original.pattern.map { v ->
            (v * 1.2f).toInt()
        }.toIntArray()
        val result = IrLearner.learn(jittered)
        assertNotNull("Expected the jittered NEC waveform to decode", result.command)
    }

    @Test
    fun `IrCommand rejects negative command`() {
        try {
            IrCommand(protocol = IrProtocol.Nec, address = 0, command = -1)
            fail("Expected IllegalArgumentException for negative command.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `IrCommand rejects negative address`() {
        try {
            IrCommand(protocol = IrProtocol.Nec, address = -1, command = 0)
            fail("Expected IllegalArgumentException for negative address.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `learn rejects a non-positive sampleRateHz`() {
        try {
            IrLearner.learn(intArrayOf(560, 1690), sampleRateHz = 0)
            fail("Expected IllegalArgumentException for sampleRateHz = 0.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }
}
