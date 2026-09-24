package com.elysium369.meet.core.emissions.preitv

import com.elysium369.meet.core.emissions.analysis.GasEstimate
import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.obd.Mode06TestResult

sealed interface PreItvPhase {
    data object Idle : PreItvPhase
    data object Preconditions : PreItvPhase
    data class CatalystWarmup(val elapsedSeconds: Int, val targetSeconds: Int = 30) : PreItvPhase
    data class IdleStabilization(val progressPct: Int) : PreItvPhase
    data class IdleCapture(val remainingSeconds: Int) : PreItvPhase
    data class AcceleratedStabilization(val currentRpm: Double, val targetRpm: Double = 2500.0) : PreItvPhase
    data class AcceleratedCapture(val remainingSeconds: Int, val currentRpm: Double) : PreItvPhase
    data object Analysis : PreItvPhase
    data class Completed(val result: PreItvResult) : PreItvPhase
    data class Aborted(val reason: String) : PreItvPhase
}

enum class PreItvVerdict {
    LOW_RISK,
    ELEVATED_RISK,
    HIGH_RISK,
    INCONCLUSIVE
}

data class PhaseMeasurement(
    val phaseName: String, // "RALENTÍ" or "ACELERADO_2500"
    val rpmMean: Double,
    val ectMean: Double,
    val coEstimate: GasEstimate,
    val hcEstimate: GasEstimate,
    val co2Estimate: GasEstimate,
    val coEvaluation: Evaluation,
    val hcEvaluation: Evaluation,
    val co2Evaluation: Evaluation,
    val lambdaEstimate: Double? = null,
    val lambdaEvaluation: Evaluation = Evaluation.NOT_APPLICABLE
)

data class PreItvResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val jurisdiction: String,
    val ruleVersion: String,
    val idle: PhaseMeasurement,
    val accelerated: PhaseMeasurement,
    val readiness: Evaluation,
    val mode06Evidence: List<Mode06TestResult>,
    val overall: PreItvVerdict,
    val confidence: Double?,
    val limitations: List<String>,
    val causalExplanations: List<String>,
    val completedAtMs: Long = System.currentTimeMillis()
)
