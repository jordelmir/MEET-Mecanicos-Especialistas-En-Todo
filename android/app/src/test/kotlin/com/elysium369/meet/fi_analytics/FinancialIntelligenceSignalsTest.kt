package com.elysium369.meet.fi_analytics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FinancialIntelligenceSignalsTest {

    @Test
    fun `sandbox signal provider returns empty list in isolated client environment`() = runBlocking {
        val provider = SandboxFinancialAggregateSignalProvider()
        val signals = provider.signals(
            startEpochMs = 1700000000000L,
            endEpochMs = 1700000000000L + 86400000L,
            cells = setOf("cell-sjo-001"),
        )
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `correlation hypothesis defaults to review required`() {
        val hypothesis = CorrelationHypothesis(
            cellId = "cell-sjo-002",
            typologyCode = "TYP_RAPID_STRUCTURING_PATTERN",
            correlation = 0.82,
        )

        assertEquals(ReviewState.REVIEW_REQUIRED, hypothesis.state)
        assertTrue(hypothesis.correlation > 0.8)
    }
}
