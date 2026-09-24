package com.elysium369.meet.core.emissions.analysis

import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import org.junit.Assert.*
import org.junit.Test

class CatalystEfficiencyAnalyzerTest {

    private val analyzer = CatalystEfficiencyAnalyzer()

    @Test
    fun `active P0420 code flags strong degradation evidence`() {
        val assessment = analyzer.assess(
            upstreamFeatures = null,
            downstreamFeatures = null,
            mode06CatalystResults = emptyList(),
            activeDtcs = listOf("P0420")
        )

        assertEquals(CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE, assessment.state)
        assertTrue(assessment.hasP0420P0430Dtc)
        assertTrue(assessment.confidence >= 0.85)
    }

    @Test
    fun `healthy converter with quiet downstream sensor reports normal evidence`() {
        // Upstream active (10 crossings), Downstream quiet (1 crossing)
        val upstream = OxygenSignalFeatures(
            sampleCount = 30,
            sampleRateHz = 5.0,
            insufficientSampleRate = false,
            p05 = 0.1,
            p50 = 0.5,
            p95 = 0.9,
            amplitude = 0.8,
            mean = 0.5,
            variance = 0.1,
            stdDev = 0.3,
            crossCount = 10,
            switchHz = 1.0,
            richToLeanMs = 200.0,
            leanToRichMs = 200.0,
            richDwellPct = 50.0,
            leanDwellPct = 50.0,
            quality = 0.95
        )
        val downstream = upstream.copy(crossCount = 1, switchHz = 0.1)

        val assessment = analyzer.assess(
            upstreamFeatures = upstream,
            downstreamFeatures = downstream,
            mode06CatalystResults = listOf(
                Mode06TestResult(
                    mid = "\$21",
                    tid = "\$41",
                    value = 0.2f,
                    minLimit = 0.0f,
                    maxLimit = 0.5f,
                    passed = true,
                    verdict = Mode06Verdict.PASS
                )
            )
        )

        assertEquals(CatalystAssessmentState.NORMAL_EVIDENCE, assessment.state)
        assertEquals(0.10, assessment.isolationRatio!!, 0.01)
    }
}
