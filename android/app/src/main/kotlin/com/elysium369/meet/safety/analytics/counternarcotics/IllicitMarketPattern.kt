package com.elysium369.meet.safety.analytics.counternarcotics

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C O U N T E R - N A R C O T I C S   P A T T E R N   A N A L Y T I C S
 *  ────────────────────────────────────────────────────────────────────
 *  Analytical layer for geospatial and temporal aggregation of
 *  documented illicit market activity signals.
 *
 *  HARD SAFETY & EPISTEMOLOGICAL CONSTITUTION:
 *  1. This system analyzes aggregated spatio-temporal patterns and
 *     institutional accountability events.
 *  2. It NEVER declares guilt, accuses specific individuals, or outputs
 *     criminal classifications like "DEALER_CONFIRMED" or "NARCO_CONFIRMED".
 *  3. Patterns are calculated exclusively from DOCUMENTED, CORROBORATED,
 *     or STRONGLY_CORROBORATED state.
 *  4. Raw citizen intake counts NEVER constitute a pattern signal.
 *  5. Outputs represent PATTERN SIGNALS with statistical uncertainty,
 *     never PROVEN CRIMES.
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class PatternTruthState {
    INSUFFICIENT_DATA,
    DOCUMENTED_PATTERN,
    CORROBORATED_PATTERN,
    DISPUTED_PATTERN,
}

@Serializable
data class IllicitMarketPattern(
    val publicCellId: String,
    val periodStartEpochMs: Long,
    val periodEndEpochMs: Long,
    val documentedClaimCount: Int,
    val independentSourceClusters: Int,
    val activeWeeks: Int,
    val journalisticSources: Int,
    val publicRecordSources: Int,
    val institutionalSources: Int,
    val institutionalResponseEvents: Int,
    val truthState: PatternTruthState,
    val policyVersion: String = "2026.V3.ANALYTICS",
) {
    /**
     * Ratio of active observation weeks vs total window duration.
     * Evaluates persistence without relying on raw report volume.
     */
    val persistenceRatio: Double
        get() {
            val totalWeeks = ((periodEndEpochMs - periodStartEpochMs) / (7L * 24 * 3600 * 1000L)).coerceAtLeast(1L)
            return (activeWeeks.toDouble() / totalWeeks.toDouble()).coerceIn(0.0, 1.0)
        }

    /**
     * Pattern is considered verified if supported by multi-source clustering
     * and multiple active weeks of corroboration.
     */
    val isMultiSourceCorroborated: Boolean
        get() = independentSourceClusters >= 2 &&
                (journalisticSources + publicRecordSources + institutionalSources) >= 1 &&
                truthState == PatternTruthState.CORROBORATED_PATTERN
}

/**
 * Deterministic engine to compute illicit market pattern signals.
 */
object CounterNarcoticsPatternEngine {

    private const val MIN_INDEPENDENT_CLUSTERS = 2
    private const val MIN_DOCUMENTED_CLAIMS = 3
    private const val MIN_ACTIVE_WEEKS = 2

    fun evaluatePattern(
        publicCellId: String,
        periodStartEpochMs: Long,
        periodEndEpochMs: Long,
        documentedClaimCount: Int,
        independentSourceClusters: Int,
        activeWeeks: Int,
        journalisticSources: Int,
        publicRecordSources: Int,
        institutionalSources: Int,
        institutionalResponseEvents: Int,
        isDisputed: Boolean = false,
    ): IllicitMarketPattern {
        val truthState = when {
            isDisputed -> PatternTruthState.DISPUTED_PATTERN
            documentedClaimCount < MIN_DOCUMENTED_CLAIMS || independentSourceClusters < 1 ->
                PatternTruthState.INSUFFICIENT_DATA
            independentSourceClusters >= MIN_INDEPENDENT_CLUSTERS &&
                    activeWeeks >= MIN_ACTIVE_WEEKS &&
                    (journalisticSources + publicRecordSources + institutionalSources) >= 1 ->
                PatternTruthState.CORROBORATED_PATTERN
            else -> PatternTruthState.DOCUMENTED_PATTERN
        }

        return IllicitMarketPattern(
            publicCellId = publicCellId,
            periodStartEpochMs = periodStartEpochMs,
            periodEndEpochMs = periodEndEpochMs,
            documentedClaimCount = documentedClaimCount,
            independentSourceClusters = independentSourceClusters,
            activeWeeks = activeWeeks,
            journalisticSources = journalisticSources,
            publicRecordSources = publicRecordSources,
            institutionalSources = institutionalSources,
            institutionalResponseEvents = institutionalResponseEvents,
            truthState = truthState,
        )
    }
}
