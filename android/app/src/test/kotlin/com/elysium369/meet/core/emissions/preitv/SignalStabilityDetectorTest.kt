package com.elysium369.meet.core.emissions.preitv

import org.junit.Assert.*
import org.junit.Test

class SignalStabilityDetectorTest {

    private val detector = SignalStabilityDetector(
        maxVariationCv = 0.08,
        maxSlopePerSec = 5.0,
        minSampleCount = 10
    )

    @Test
    fun `insufficient samples reports not stable`() {
        val samples = listOf(
            0L to 750.0,
            100L to 752.0,
            200L to 749.0
        )
        val result = detector.evaluate(samples)
        assertFalse("Less than 10 samples cannot declare stability", result.stable)
    }

    @Test
    fun `erratic signal with high variation reports not stable`() {
        val samples = mutableListOf<Pair<Long, Double>>()
        // Fluctuates wildly between 600 and 1100 RPM
        for (i in 0 until 15) {
            val v = if (i % 2 == 0) 600.0 else 1100.0
            samples.add((i * 100L) to v)
        }
        val result = detector.evaluate(samples)
        assertFalse("Erratic oscillating signal must not declare stable", result.stable)
        assertTrue(result.variation!! > 0.08)
    }

    @Test
    fun `stable idle RPM within tolerances reports stable`() {
        val samples = mutableListOf<Pair<Long, Double>>()
        // Very tight idle RPM ~745-752 RPM
        val rpms = listOf(748.0, 750.0, 751.0, 749.0, 752.0, 748.0, 750.0, 750.0, 749.0, 751.0, 750.0, 749.0)
        for (i in rpms.indices) {
            samples.add((i * 100L) to rpms[i])
        }
        val result = detector.evaluate(samples)
        assertTrue("Tight idle signal must declare stable", result.stable)
        assertTrue("Stability score must be high", result.score > 0.7)
    }
}
