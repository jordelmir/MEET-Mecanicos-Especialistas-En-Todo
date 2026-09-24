package com.elysium369.meet.core.emissions.analysis

import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.emissions.regulations.CostaRicaGasolineRules
import com.elysium369.meet.core.emissions.regulations.GasMetric
import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import com.elysium369.meet.core.emissions.preitv.PreItvVerdict
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import com.elysium369.meet.core.obd.ReadinessResult

/**
 * Unified input contract for live emissions telemetry and Pre-ITV inspection phases.
 */
data class EmissionsEngineInput(
    val rpm: Double?,
    val ectC: Double?,
    val stftPct: Double?,
    val ltftPct: Double?,
    val lambda: Double?,
    val misfireCount: Int = 0,
    val o2UpstreamFeatures: OxygenSignalFeatures? = null,
    val catalystAssessment: CatalystAssessment? = null,
    val mode06Results: List<Mode06TestResult> = emptyList(),
    val readinessResult: ReadinessResult? = null,
    val activeProfile: RegulatoryVehicleProfile = RegulatoryVehicleProfile(),
    val isAcceleratedRpm: Boolean = false,
    val isConnected: Boolean = true,
    val physicalSampleCount: Int = 0
)

/**
 * Authoritative evaluation output from the Emissions Engine.
 */
data class EmissionsEngineOutput(
    val combustionAssessment: CombustionAssessment,
    val coEvaluation: Evaluation,
    val hcEvaluation: Evaluation,
    val co2Evaluation: Evaluation,
    val lambdaEvaluation: Evaluation,
    val readinessEvaluation: Evaluation,
    val mode06Evaluation: Evaluation,
    val overallVerdict: PreItvVerdict,
    val causalExplanations: List<String>,
    val isConnected: Boolean
)

/**
 * Single authoritative Emissions & Pre-ITV Engine.
 * Upholds the invariant:
 * - Live Gas Analyzer and Pre-ITV inspection use the exact same physics and regulatory rules.
 * - LOW_RISK is strictly impossible if readiness is incomplete, Mode 06 has fails, or trims/lambda diverge.
 * - Disconnected state never presents synthetic passing numbers.
 */
class EmissionsEngine(
    private val combustionEstimator: CombustionRiskEstimator = CombustionRiskEstimator()
) {

    fun evaluate(input: EmissionsEngineInput): EmissionsEngineOutput {
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(input.activeProfile)
        val explanations = mutableListOf<String>()

        // 1. Connection Guard
        if (!input.isConnected) {
            val emptyGas = GasEstimate(0.0, 0.0, 0.0, "% vol", modelVersion = "DISCONNECTED")
            val emptyAssessment = CombustionAssessment(
                coRisk = CombustionRiskLevel.INCONCLUSIVE,
                hcRisk = CombustionRiskLevel.INCONCLUSIVE,
                coEstimate = emptyGas,
                hcEstimate = GasEstimate(0.0, 0.0, 0.0, "ppm", modelVersion = "DISCONNECTED"),
                co2Estimate = emptyGas,
                combinedTrimPct = null,
                misfireCount = 0,
                confidence = 0.0,
                causalExplanations = listOf("ECU desconectada. Sin señal física de telemetría.")
            )
            return EmissionsEngineOutput(
                combustionAssessment = emptyAssessment,
                coEvaluation = Evaluation.NOT_APPLICABLE,
                hcEvaluation = Evaluation.NOT_APPLICABLE,
                co2Evaluation = Evaluation.NOT_APPLICABLE,
                lambdaEvaluation = Evaluation.NOT_APPLICABLE,
                readinessEvaluation = Evaluation.INCONCLUSIVE,
                mode06Evaluation = Evaluation.INCONCLUSIVE,
                overallVerdict = PreItvVerdict.INCONCLUSIVE,
                causalExplanations = listOf("Vehículo desconectado."),
                isConnected = false
            )
        }

        // 2. Physical Combustion Assessment
        // Only evaluate sluggish O2 switching if sufficient physical samples exist in window (avoid flatline false positives)
        val validO2Features = if (input.physicalSampleCount >= 8) input.o2UpstreamFeatures else null

        val assessment = combustionEstimator.estimate(
            stftPct = input.stftPct,
            ltftPct = input.ltftPct,
            lambda = input.lambda,
            coolantC = input.ectC,
            misfireCount = input.misfireCount,
            o2UpstreamFeatures = validO2Features,
            catalystAssessment = input.catalystAssessment,
            isAcceleratedRpm = input.isAcceleratedRpm
        )
        explanations.addAll(assessment.causalExplanations)

        // 3. Regulatory Limit Evaluation (Idle or Accelerated)
        val limits = if (input.isAcceleratedRpm) ruleSet.acceleratedLimits else ruleSet.idleLimits

        val coLimit = limits.find { it.metric == GasMetric.CO }
        val hcLimit = limits.find { it.metric == GasMetric.HC }
        val co2Limit = limits.find { it.metric == GasMetric.CO2 }
        val lambdaLimit = limits.find { it.metric == GasMetric.LAMBDA }

        val coEval = coLimit?.evaluate(assessment.coEstimate) ?: Evaluation.NOT_APPLICABLE
        val hcEval = hcLimit?.evaluate(assessment.hcEstimate) ?: Evaluation.NOT_APPLICABLE
        val co2Eval = co2Limit?.evaluate(assessment.co2Estimate) ?: Evaluation.NOT_APPLICABLE
        val lambdaEval = if (input.lambda != null && lambdaLimit != null) {
            lambdaLimit.evaluatePoint(input.lambda)
        } else {
            Evaluation.NOT_APPLICABLE
        }

        // 4. Readiness Evaluation
        val readinessEval: Evaluation
        val readiness = input.readinessResult
        if (readiness == null) {
            readinessEval = Evaluation.INCONCLUSIVE
        } else {
            val supported = readiness.monitors.filter { it.available }
            val incompleteCount = supported.count { !it.complete }
            val catalystIncomplete = supported.any { it.name.contains("Catalyst", ignoreCase = true) && !it.complete }
            val o2Incomplete = supported.any { it.name.contains("O2", ignoreCase = true) && !it.complete }

            when {
                readiness.milOn || readiness.dtcCount > 0 -> {
                    readinessEval = Evaluation.FAIL
                    explanations.add("Luz MIL encendida o ${readiness.dtcCount} DTCs registrados: rechazo automático en ITV")
                }
                catalystIncomplete || o2Incomplete || incompleteCount >= 2 -> {
                    readinessEval = Evaluation.FAIL
                    val incNames = supported.filter { !it.complete }.joinToString { it.name }
                    explanations.add("Monitores de emisión incompletos ($incNames): causa de rechazo")
                }
                incompleteCount == 1 -> {
                    readinessEval = Evaluation.INCONCLUSIVE
                    explanations.add("1 monitor incompleto (${supported.first { !it.complete }.name})")
                }
                else -> {
                    readinessEval = Evaluation.PASS
                }
            }
        }

        // 5. Mode $06 Evidence Evaluation
        val m06Fails = input.mode06Results.filter { it.verdict == Mode06Verdict.FAIL }
        val mode06Eval = when {
            m06Fails.isNotEmpty() -> {
                explanations.add("${m06Fails.size} prueba(s) Mode \$06 reprobadas por la ECU")
                Evaluation.FAIL
            }
            input.mode06Results.any { it.verdict == Mode06Verdict.PASS } -> Evaluation.PASS
            else -> Evaluation.INCONCLUSIVE
        }

        // 6. Overall Verdict Synthesis (Strict Invariant: Zero False Passes)
        val anyGasFail = coEval == Evaluation.FAIL || hcEval == Evaluation.FAIL ||
            co2Eval == Evaluation.FAIL || lambdaEval == Evaluation.FAIL

        val anyGasInconclusive = coEval == Evaluation.INCONCLUSIVE || hcEval == Evaluation.INCONCLUSIVE ||
            input.physicalSampleCount < 5

        val overallVerdict = when {
            anyGasFail || readinessEval == Evaluation.FAIL || mode06Eval == Evaluation.FAIL -> {
                PreItvVerdict.HIGH_RISK
            }
            anyGasInconclusive || readinessEval == Evaluation.INCONCLUSIVE -> {
                PreItvVerdict.ELEVATED_RISK
            }
            coEval == Evaluation.PASS && hcEval == Evaluation.PASS && readinessEval == Evaluation.PASS -> {
                PreItvVerdict.LOW_RISK
            }
            else -> {
                PreItvVerdict.INCONCLUSIVE
            }
        }

        return EmissionsEngineOutput(
            combustionAssessment = assessment,
            coEvaluation = coEval,
            hcEvaluation = hcEval,
            co2Evaluation = co2Eval,
            lambdaEvaluation = lambdaEval,
            readinessEvaluation = readinessEval,
            mode06Evaluation = mode06Eval,
            overallVerdict = overallVerdict,
            causalExplanations = explanations.distinct(),
            isConnected = true
        )
    }
}
