package com.elysium369.meet.core.emissions.analysis

import org.junit.Assert.*
import org.junit.Test

class WaveformAutoDiagnosticianTest {

    private val diagnostician = WaveformAutoDiagnostician()
    private val analyzer = OxygenSignalAnalyzer()

    @Test
    fun `when disconnected returns disconnected diagnosis`() {
        val result = diagnostician.diagnose(
            upstreamFeatures = null,
            isConnected = false
        )

        assertEquals(WaveformClinicalState.DISCONNECTED, result.state)
        assertEquals(0, result.metrics.healthIndexPct)
    }

    @Test
    fun `detects exhaust leak when lambda is lean and trims are strongly negative`() {
        val features = analyzer.analyze(
            listOf(
                OxygenSample(1000L, 0.70),
                OxygenSample(2000L, 0.20),
                OxygenSample(3000L, 0.75),
                OxygenSample(4000L, 0.15)
            )
        )

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            stftPct = -8.0,
            ltftPct = -8.3, // -16.3%
            lambda = 1.25, // Lean
            isConnected = true
        )

        assertEquals(WaveformClinicalState.EXHAUST_LEAK_FALSE_LEAN, result.state)
        assertTrue(result.summary.contains("entrada de aire ambiental"))
    }

    @Test
    fun `detects dead or poisoned sensor when amplitude is below 0_20V`() {
        // Flatline near 0.45V
        val flatSamples = (1..10).map { OxygenSample(it * 500L, 0.45 + (it % 2) * 0.05) }
        val features = analyzer.analyze(flatSamples)

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            isConnected = true
        )

        assertEquals(WaveformClinicalState.DEAD_OR_POISONED, result.state)
        assertTrue(result.summary.contains("apenas oscila"))
    }

    @Test
    fun `detects biased rich when signal dwells continuously above 0_65V`() {
        val richSamples = (1..12).map { OxygenSample(it * 500L, 0.75 + (it % 3) * 0.05) }
        val features = analyzer.analyze(richSamples)

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            isConnected = true
        )

        assertEquals(WaveformClinicalState.BIASED_RICH, result.state)
        assertTrue(result.summary.contains("zona alta"))
    }

    @Test
    fun `detects biased lean when signal dwells continuously below 0_25V`() {
        val leanSamples = (1..12).map { OxygenSample(it * 500L, 0.12 + (it % 2) * 0.04) }
        val features = analyzer.analyze(leanSamples)

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            isConnected = true
        )

        assertEquals(WaveformClinicalState.BIASED_LEAN, result.state)
        assertTrue(result.summary.contains("zona baja"))
    }

    @Test
    fun `detects catalyst depleted when downstream tracks upstream`() {
        val samples = (1..12).map { OxygenSample(it * 500L, if (it % 2 == 0) 0.80 else 0.15) }
        val features = analyzer.analyze(samples)

        val catAssessment = CatalystAssessment(
            state = CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE,
            isolationRatio = 0.85, // Severe tracking
            upstreamCrossCount = 10,
            downstreamCrossCount = 8,
            mode06Passed = false,
            hasP0420P0430Dtc = false,
            confidence = 0.90,
            explanation = "Downstream tracks upstream"
        )

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            catalystAssessment = catAssessment,
            isConnected = true
        )

        assertEquals(WaveformClinicalState.CATALYST_DEPLETED, result.state)
        assertTrue(result.summary.contains("sensor posterior B1S2 oscila al ritmo del frontal"))
    }

    @Test
    fun `detects healthy closed loop with energetic switching`() {
        // Alternating every 500ms -> 1 Hz switching, amplitude 0.70V
        val healthySamples = (1..16).map { OxygenSample(it * 500L, if (it % 2 == 0) 0.85 else 0.15) }
        val features = analyzer.analyze(healthySamples)

        val result = diagnostician.diagnose(
            upstreamFeatures = features,
            isConnected = true
        )

        assertEquals(WaveformClinicalState.HEALTHY_CLOSED_LOOP, result.state)
        assertTrue(result.metrics.healthIndexPct >= 80)
    }
}
