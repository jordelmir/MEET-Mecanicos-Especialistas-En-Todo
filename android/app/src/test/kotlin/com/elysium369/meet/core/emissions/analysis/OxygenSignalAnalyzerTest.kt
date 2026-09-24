package com.elysium369.meet.core.emissions.analysis

import org.junit.Assert.*
import org.junit.Test

class OxygenSignalAnalyzerTest {

    private val analyzer = OxygenSignalAnalyzer(minSampleRateHz = 2.0)

    @Test
    fun `slow sampling rate flags insufficient sample rate and suppresses switch frequency`() {
        // 5 samples over 10 seconds = 0.5 Hz (< 2.0 Hz)
        val samples = listOf(
            OxygenSample(0L, 0.8),
            OxygenSample(2500L, 0.1),
            OxygenSample(5000L, 0.7),
            OxygenSample(7500L, 0.2),
            OxygenSample(10000L, 0.8)
        )

        val features = analyzer.analyze(samples)

        assertTrue("Slow sample rate must flag insufficientSampleRate", features.insufficientSampleRate)
        assertNull("Switch frequency must be suppressed when sample rate is insufficient", features.switchHz)
        assertNull("Transition times must be suppressed when sample rate is insufficient", features.richToLeanMs)
    }

    @Test
    fun `healthy 10 Hz sampling accurately captures switching frequency and crossings`() {
        val samples = mutableListOf<OxygenSample>()
        // 10 Hz for 4 seconds (40 samples), oscillating between 0.15V and 0.85V at 1 Hz
        for (i in 0 until 40) {
            val t = i * 100L
            val v = if ((i / 5) % 2 == 0) 0.82 else 0.18
            samples.add(OxygenSample(t, v))
        }

        val features = analyzer.analyze(samples)

        assertFalse(features.insufficientSampleRate)
        assertTrue(features.crossCount >= 6)
        assertNotNull(features.switchHz)
        assertTrue("Switching Hz should be around 0.9 - 1.1 Hz", features.switchHz!! in 0.8..1.2)
        assertEquals(0.82 - 0.18, features.amplitude!!, 0.05)
    }
}
