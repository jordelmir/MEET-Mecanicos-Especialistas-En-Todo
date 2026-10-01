package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.SafetyObservatoryCellV3
import com.elysium369.meet.safety.data.SafetyObservatoryFilters
import com.elysium369.meet.safety.data.SafetyObservatoryProjectionV3
import com.elysium369.meet.safety.data.v3Parameters
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SafetyObservatoryV3PolicyTest {
    @Test fun defaultsUseWholeDelayedUtcWeeksAndCurrentRpcNames() {
        val parameters = SafetyObservatoryFilters().v3Parameters(Instant.parse("2026-10-01T15:00:00Z"))
        assertEquals("2026-09-21T00:00:00Z", parameters["p_until"]?.jsonPrimitive?.content)
        assertFalse(parameters.containsKey("p_to"))
        assertFalse(parameters.containsKey("latitude"))
    }
    @Test fun incompleteBoundaryWeeksAreExcludedWithoutInventingData() {
        val parameters = SafetyObservatoryFilters(from="2026-01-07T01:00:00Z",to="2026-02-04T12:00:00Z")
            .v3Parameters(Instant.parse("2026-10-01T00:00:00Z"))
        assertEquals("2026-01-12T00:00:00Z", parameters["p_from"]?.jsonPrimitive?.content)
        assertEquals("2026-02-02T00:00:00Z", parameters["p_until"]?.jsonPrimitive?.content)
    }
    @Test fun unavailableSensitiveMetricsAreNeverInterpretedAsMeasuredZero() {
        val metrics = SafetyObservatoryProjectionV3("SAFETY-OBSERVATORY-V3", listOf(
            SafetyObservatoryCellV3("coarse-cell","DRUG_SALE_ACTIVITY","2026-01-05",5)),"SMALL_CELLS_OMITTED").toMetrics()
        assertTrue(metrics.privacy_suppressed)
        assertFalse(metrics.sensitive_metrics_available)
        assertEquals(5L, metrics.drugs_count)
        assertFalse(metrics.hasResolutionData)
    }
    @Test(expected=IllegalArgumentException::class) fun noCompleteSafeWeekIsRejected() {
        SafetyObservatoryFilters(from="2026-09-29T00:00:00Z",to="2026-10-01T00:00:00Z")
            .v3Parameters(Instant.parse("2026-10-01T00:00:00Z"))
    }
}
