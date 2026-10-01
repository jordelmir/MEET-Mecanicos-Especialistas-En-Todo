package com.elysium369.meet.safety.analytics.counternarcotics

import org.junit.Assert.*
import org.junit.Test

class CounterNarcoticsPatternEngineTest {

    @Test
    fun `insufficient data when claims or clusters are below threshold`() {
        val pattern = CounterNarcoticsPatternEngine.evaluatePattern(
            publicCellId = "cell-001",
            periodStartEpochMs = 1700000000000L,
            periodEndEpochMs = 1700000000000L + (14L * 24 * 3600 * 1000L),
            documentedClaimCount = 1,
            independentSourceClusters = 1,
            activeWeeks = 1,
            journalisticSources = 0,
            publicRecordSources = 0,
            institutionalSources = 0,
            institutionalResponseEvents = 0,
        )

        assertEquals(PatternTruthState.INSUFFICIENT_DATA, pattern.truthState)
        assertFalse(pattern.isMultiSourceCorroborated)
    }

    @Test
    fun `documented pattern when minimum claims reached without full corroboration`() {
        val pattern = CounterNarcoticsPatternEngine.evaluatePattern(
            publicCellId = "cell-002",
            periodStartEpochMs = 1700000000000L,
            periodEndEpochMs = 1700000000000L + (21L * 24 * 3600 * 1000L),
            documentedClaimCount = 4,
            independentSourceClusters = 1,
            activeWeeks = 2,
            journalisticSources = 0,
            publicRecordSources = 0,
            institutionalSources = 0,
            institutionalResponseEvents = 0,
        )

        assertEquals(PatternTruthState.DOCUMENTED_PATTERN, pattern.truthState)
        assertFalse(pattern.isMultiSourceCorroborated)
    }

    @Test
    fun `corroborated pattern when independent clusters and public sources confirm`() {
        val pattern = CounterNarcoticsPatternEngine.evaluatePattern(
            publicCellId = "cell-003",
            periodStartEpochMs = 1700000000000L,
            periodEndEpochMs = 1700000000000L + (28L * 24 * 3600 * 1000L),
            documentedClaimCount = 8,
            independentSourceClusters = 3,
            activeWeeks = 3,
            journalisticSources = 1,
            publicRecordSources = 1,
            institutionalSources = 0,
            institutionalResponseEvents = 1,
        )

        assertEquals(PatternTruthState.CORROBORATED_PATTERN, pattern.truthState)
        assertTrue(pattern.isMultiSourceCorroborated)
        assertTrue(pattern.persistenceRatio > 0.5)
    }

    @Test
    fun `disputed pattern overrides corroboration when formal dispute exists`() {
        val pattern = CounterNarcoticsPatternEngine.evaluatePattern(
            publicCellId = "cell-004",
            periodStartEpochMs = 1700000000000L,
            periodEndEpochMs = 1700000000000L + (28L * 24 * 3600 * 1000L),
            documentedClaimCount = 10,
            independentSourceClusters = 3,
            activeWeeks = 3,
            journalisticSources = 2,
            publicRecordSources = 0,
            institutionalSources = 0,
            institutionalResponseEvents = 0,
            isDisputed = true,
        )

        assertEquals(PatternTruthState.DISPUTED_PATTERN, pattern.truthState)
        assertFalse(pattern.isMultiSourceCorroborated)
    }
}
