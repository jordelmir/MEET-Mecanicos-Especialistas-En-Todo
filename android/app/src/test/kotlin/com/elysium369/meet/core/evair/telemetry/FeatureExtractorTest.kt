package com.elysium369.meet.core.evair.telemetry

import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.QualitySummary
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import com.elysium369.meet.core.evair.domain.TelemetryWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {

    @Test
    fun `extract from empty window returns default features`() {
        val window = TelemetryWindow(
            pid = "010C",
            parameterName = "Engine RPM",
            unit = "RPM",
            startTimestampMs = 0L,
            endTimestampMs = 0L,
            durationMs = 0L,
            sampleCount = 0,
            samples = emptyList(),
            qualitySummary = QualitySummary()
        )

        val features = FeatureExtractor.extract(window)
        assertEquals(0, features.count)
        assertEquals(0.0, features.mean, 0.001)
        assertEquals(0.0, features.min, 0.001)
        assertEquals(0.0, features.max, 0.001)
        assertEquals(1.0, features.missingRate, 0.001)
    }

    @Test
    fun `extract features computes correct statistics for known values`() {
        // Given 5 samples: 10, 20, 30, 40, 50
        val samples = listOf(
            createPoint("010C", 10.0, 1000L),
            createPoint("010C", 20.0, 2000L),
            createPoint("010C", 30.0, 3000L),
            createPoint("010C", 40.0, 4000L),
            createPoint("010C", 50.0, 5000L)
        )

        val window = TelemetryWindow(
            pid = "010C",
            parameterName = "Engine RPM",
            unit = "RPM",
            startTimestampMs = 1000L,
            endTimestampMs = 5000L,
            durationMs = 4000L,
            sampleCount = 5,
            samples = samples,
            qualitySummary = QualitySummary(goodCount = 5)
        )

        val features = FeatureExtractor.extract(window)
        assertEquals(5, features.count)
        assertEquals(10.0, features.min, 0.001)
        assertEquals(50.0, features.max, 0.001)
        assertEquals(30.0, features.mean, 0.001)
        assertEquals(40.0, features.delta, 0.001) // 50 - 10

        // Delta is 40 over 4 seconds -> slope is 10.0 per second
        assertEquals(10.0, features.slopePerSecond, 0.001)

        // Quantiles
        assertNotNull(features.p50)
        assertEquals(30.0, features.p50!!, 0.5)

        assertEquals(0.0, features.missingRate, 0.001)
        assertEquals(0.0, features.staleRate, 0.001)
    }

    @Test
    fun `stale and invalid samples are filtered from mathematical stats`() {
        val samples = listOf(
            createPoint("010C", 100.0, 1000L, DataQuality.GOOD),
            createPoint("010C", 999.0, 2000L, DataQuality.INVALID),
            createPoint("010C", 200.0, 3000L, DataQuality.GOOD),
            createPoint("010C", 888.0, 4000L, DataQuality.STALE)
        )

        val window = TelemetryWindow(
            pid = "010C",
            parameterName = "Engine RPM",
            unit = "RPM",
            startTimestampMs = 1000L,
            endTimestampMs = 4000L,
            durationMs = 3000L,
            sampleCount = 4,
            samples = samples,
            qualitySummary = QualitySummary(goodCount = 2, invalidCount = 1, staleCount = 1)
        )

        val features = FeatureExtractor.extract(window)
        assertEquals(4, features.count)
        assertEquals(100.0, features.min, 0.001)
        assertEquals(200.0, features.max, 0.001)
        assertEquals(150.0, features.mean, 0.001)
        assertEquals(0.25, features.missingRate, 0.001)
        assertEquals(0.25, features.staleRate, 0.001)
    }

    private fun createPoint(
        pid: String,
        value: Double,
        timestampMs: Long,
        quality: DataQuality = DataQuality.GOOD
    ): TelemetryPoint {
        return TelemetryPoint(
            monotonicTimestampNs = timestampMs * 1_000_000L,
            wallClockTimestampMs = timestampMs,
            pid = pid,
            value = value,
            unit = "RPM",
            quality = quality
        )
    }
}
