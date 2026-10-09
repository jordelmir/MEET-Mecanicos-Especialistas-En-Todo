package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.SafetyObservatoryCellV3
import com.elysium369.meet.safety.data.SafetyObservatoryDataState
import com.elysium369.meet.safety.data.SafetyObservatoryMetrics
import com.elysium369.meet.safety.data.SafetyObservatoryProjectionV3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyObservatoryMetricsIntegrityTest {
    @Test
    fun unavailableMetricsAreNotPresentedAsMeasuredZeros() {
        val metrics = SafetyObservatoryMetrics.unavailable()

        assertEquals(SafetyObservatoryDataState.UNAVAILABLE, metrics.data_state)
        assertTrue(metrics.privacy_suppressed)
        assertFalse(metrics.sensitive_metrics_available)
        assertEquals(0L, metrics.public_point_count)
        assertEquals(0L, metrics.independent_source_count)
        assertEquals(0L, metrics.civil_source_count)
        assertEquals(0L, metrics.journalistic_source_count)
        assertEquals(emptyList<Pair<String, Long>>(), metrics.sourceBreakdown())
    }

    @Test
    fun V3ProjectionKeepsActualCountsAndSuppressesSourceBreakdown() {
        val metrics = SafetyObservatoryProjectionV3(
            policy_version = "SAFETY-OBSERVATORY-V3",
            cells = listOf(
                SafetyObservatoryCellV3("cell-a", "HOMICIDE", "2026-01-05", 5),
                SafetyObservatoryCellV3("cell-b", "VIOLENT_INCIDENT", "2026-01-12", 7),
            ),
            suppression = "SMALL_CELLS_OMITTED",
        ).toMetrics()

        assertEquals(SafetyObservatoryDataState.REMOTE_AGGREGATE, metrics.data_state)
        assertEquals(12L, metrics.public_point_count)
        assertEquals(5L, metrics.homicide_count)
        assertEquals(7L, metrics.violence_count)
        assertTrue(metrics.privacy_suppressed)
        assertFalse(metrics.sensitive_metrics_available)
        assertEquals(0L, metrics.independent_source_count)
        assertEquals(emptyList<Pair<String, Long>>(), metrics.sourceBreakdown())
    }
}
