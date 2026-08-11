package com.elysium369.meet.core.diagnostics

import kotlin.math.ln

data class CalibratedHypothesisPrior(
    val hypothesisId: String,
    val probability: Double,
)

data class CalibratedTestOutcome(
    val outcomeId: String,
    val probabilityGivenHypothesis: Map<String, Double>,
)

data class DiagnosticTestDecisionCandidate(
    val testId: String,
    val possibleOutcomes: List<CalibratedTestOutcome>,
    val normalizedCost: Double,
    val normalizedTimePenalty: Double,
    val normalizedSafetyRisk: Double,
    val requiredTools: List<String>,
    val calibrationDatasetId: String?,
)

data class DiagnosticTestDecisionScore(
    val testId: String,
    val expectedInformationGainBits: Double,
    val utility: Double,
    val calibrated: Boolean,
    val reason: String,
)

/**
 * Shannon decision model. It refuses quantitative output unless every
 * conditional probability is present, bounded and tied to a calibration set.
 */
object DiagnosticTestDecisionModel {
    fun score(
        priors: List<CalibratedHypothesisPrior>,
        candidate: DiagnosticTestDecisionCandidate,
        costWeight: Double = 0.25,
        timeWeight: Double = 0.15,
        riskWeight: Double = 0.60,
    ): DiagnosticTestDecisionScore {
        val hypothesisIds = priors.map { it.hypothesisId }.toSet()
        val complete = candidate.calibrationDatasetId?.isNotBlank() == true &&
            priors.isNotEmpty() &&
            priors.all { it.probability in 0.0..1.0 } &&
            priors.sumOf { it.probability } > 0.0 &&
            candidate.possibleOutcomes.size >= 2 &&
            candidate.possibleOutcomes.all { outcome ->
                outcome.probabilityGivenHypothesis.keys.containsAll(hypothesisIds) &&
                    outcome.probabilityGivenHypothesis.values.all { it in 0.0..1.0 }
            } &&
            hypothesisIds.all { hypothesisId ->
                val total = candidate.possibleOutcomes.sumOf {
                    it.probabilityGivenHypothesis.getValue(hypothesisId)
                }
                total in 0.999..1.001
            }
        if (!complete) {
            return DiagnosticTestDecisionScore(
                candidate.testId,
                expectedInformationGainBits = 0.0,
                utility = Double.NEGATIVE_INFINITY,
                calibrated = false,
                reason = "CALIBRATION_REQUIRED: matriz P(resultado|hipótesis) incompleta o sin dataset.",
            )
        }

        val totalPrior = priors.sumOf { it.probability }
        val normalized = priors.associate { it.hypothesisId to it.probability / totalPrior }
        val priorEntropy = entropy(normalized.values)
        val expectedPosteriorEntropy = candidate.possibleOutcomes.sumOf { outcome ->
            val outcomeProbability = normalized.entries.sumOf { (hypothesisId, prior) ->
                prior * outcome.probabilityGivenHypothesis.getValue(hypothesisId)
            }
            if (outcomeProbability <= 0.0) return@sumOf 0.0
            val posterior = normalized.map { (hypothesisId, prior) ->
                prior * outcome.probabilityGivenHypothesis.getValue(hypothesisId) / outcomeProbability
            }
            outcomeProbability * entropy(posterior)
        }
        val eig = (priorEntropy - expectedPosteriorEntropy).coerceAtLeast(0.0)
        val utility = eig -
            candidate.normalizedCost.coerceIn(0.0, 1.0) * costWeight -
            candidate.normalizedTimePenalty.coerceIn(0.0, 1.0) * timeWeight -
            candidate.normalizedSafetyRisk.coerceIn(0.0, 1.0) * riskWeight
        return DiagnosticTestDecisionScore(
            candidate.testId,
            expectedInformationGainBits = eig,
            utility = utility,
            calibrated = true,
            reason = "EIG calibrado con ${candidate.calibrationDatasetId}; utilidad penaliza costo, tiempo y riesgo.",
        )
    }

    private fun entropy(probabilities: Collection<Double>): Double = probabilities.sumOf { p ->
        if (p <= 0.0) 0.0 else -p * (ln(p) / ln(2.0))
    }
}
