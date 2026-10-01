package com.elysium369.meet.fi_analytics

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  F I N A N C I A L   I N T E L L I G E N C E   S I G N A L S
 *  ────────────────────────────────────────────────────────────
 *  Isolated domain for financial aggregate telemetry and statistical
 *  typology correlation.
 *
 *  HARD ARCHITECTURAL BOUNDARY:
 *  1. This domain NEVER stores raw banking, account, or personal financial
 *     records in the mobile client or public Supabase layer.
 *  2. Real financial intelligence correlation requires an EXTERNAL_GATE:
 *     - Lawful authority and Financial Intelligence Unit (FIU) bilateral agreement
 *     - Isolated tenant infrastructure with hardware security modules (HSM)
 *     - Strict audit retention and Data Processing Agreement (DPA)
 *  3. In the public APK and client runtime, only sanitized aggregate signals
 *     and correlation hypotheses are supported.
 *  4. Hypotheses represent statistical signals for review; they NEVER
 *     label or accuse subjects ("isMoneyLaunderer = true" is strictly prohibited).
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
enum class ReviewState {
    REVIEW_REQUIRED,
    AUDITED,
    DISMISSED,
}

@Serializable
data class FinancialAggregateSignal(
    val cellId: String,
    val periodStartEpochMs: Long,
    val periodEndEpochMs: Long,
    val typologyCode: String,
    val normalizedValue: Double,
    val sourceAuthority: String,
)

@Serializable
data class CorrelationHypothesis(
    val cellId: String,
    val typologyCode: String,
    val correlation: Double,
    val state: ReviewState = ReviewState.REVIEW_REQUIRED,
    val sampleSize: Int = 0,
    val confidencePValue: Double = 1.0,
)

interface FinancialAggregateSignalProvider {
    suspend fun signals(
        startEpochMs: Long,
        endEpochMs: Long,
        cells: Set<String>,
    ): List<FinancialAggregateSignal>
}

/**
 * Offline/synthetic implementation for local sandbox validation.
 * Real banking/FIU feeds are gated behind lawful external infrastructure.
 */
class SandboxFinancialAggregateSignalProvider : FinancialAggregateSignalProvider {
    override suspend fun signals(
        startEpochMs: Long,
        endEpochMs: Long,
        cells: Set<String>,
    ): List<FinancialAggregateSignal> {
        // Return empty or deterministic synthetic signals for testing
        return emptyList()
    }
}
