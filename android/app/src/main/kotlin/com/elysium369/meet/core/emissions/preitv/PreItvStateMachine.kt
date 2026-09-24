package com.elysium369.meet.core.emissions.preitv

import com.elysium369.meet.core.emissions.analysis.CombustionRiskEstimator
import com.elysium369.meet.core.emissions.analysis.GasEstimate
import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.emissions.regulations.CostaRicaGasolineRules
import com.elysium369.meet.core.emissions.regulations.GasMetric
import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreItvStateMachine(
    private val stabilityDetector: SignalStabilityDetector = SignalStabilityDetector(),
    private val combustionEstimator: CombustionRiskEstimator = CombustionRiskEstimator()
) {

    private val _phase = MutableStateFlow<PreItvPhase>(PreItvPhase.Idle)
    val phase: StateFlow<PreItvPhase> = _phase.asStateFlow()

    private var activeProfile: RegulatoryVehicleProfile = RegulatoryVehicleProfile(modelYear = 2005)
    private var mode06Results: List<Mode06TestResult> = emptyList()

    private val idleSamples = mutableListOf<Pair<Long, Double>>()
    private val idleRpmList = mutableListOf<Double>()
    private val idleEctList = mutableListOf<Double>()
    private val idleStftList = mutableListOf<Double>()
    private val idleLtftList = mutableListOf<Double>()
    private val idleLambdaList = mutableListOf<Double>()

    private val accelSamples = mutableListOf<Pair<Long, Double>>()
    private val accelRpmList = mutableListOf<Double>()
    private val accelEctList = mutableListOf<Double>()
    private val accelStftList = mutableListOf<Double>()
    private val accelLtftList = mutableListOf<Double>()
    private val accelLambdaList = mutableListOf<Double>()

    private var phaseStartTimeMs = 0L

    fun start(profile: RegulatoryVehicleProfile) {
        activeProfile = profile
        idleSamples.clear()
        idleRpmList.clear()
        idleEctList.clear()
        idleStftList.clear()
        idleLtftList.clear()
        idleLambdaList.clear()

        accelSamples.clear()
        accelRpmList.clear()
        accelEctList.clear()
        accelStftList.clear()
        accelLtftList.clear()
        accelLambdaList.clear()

        _phase.value = PreItvPhase.Preconditions
    }

    fun setMode06Evidence(results: List<Mode06TestResult>) {
        mode06Results = results
    }

    fun abort(reason: String) {
        _phase.value = PreItvPhase.Aborted(reason)
    }

    fun reset() {
        _phase.value = PreItvPhase.Idle
    }

    fun tickTelemetry(
        rpm: Double?,
        ectC: Double?,
        speedKmh: Double?,
        stftPct: Double?,
        ltftPct: Double?,
        lambda: Double?,
        transmissionConfirmedParkOrNeutral: Boolean = true
    ) {
        val now = System.currentTimeMillis()

        when (val current = _phase.value) {
            PreItvPhase.Idle -> {}

            PreItvPhase.Preconditions -> {
                if (rpm == null || rpm < 400.0) return // Engine not running
                if (ectC != null && ectC < 65.0) return // Engine cold
                if (speedKmh != null && speedKmh > 2.0) return // Vehicle moving
                if (!transmissionConfirmedParkOrNeutral) return

                // Preconditions met -> Move to Catalyst Warmup
                phaseStartTimeMs = now
                _phase.value = PreItvPhase.CatalystWarmup(elapsedSeconds = 0, targetSeconds = 20)
            }

            is PreItvPhase.CatalystWarmup -> {
                val elapsed = ((now - phaseStartTimeMs) / 1000).toInt()
                if (elapsed >= current.targetSeconds) {
                    phaseStartTimeMs = now
                    idleSamples.clear()
                    _phase.value = PreItvPhase.IdleStabilization(progressPct = 0)
                } else {
                    _phase.value = PreItvPhase.CatalystWarmup(elapsedSeconds = elapsed, targetSeconds = current.targetSeconds)
                }
            }

            is PreItvPhase.IdleStabilization -> {
                if (rpm != null) {
                    idleSamples.add(now to rpm)
                    if (idleSamples.size > 30) idleSamples.removeAt(0)
                    val stability = stabilityDetector.evaluate(idleSamples)
                    val progress = (stability.score * 100).toInt().coerceIn(0, 100)
                    if (stability.stable && rpm in 550.0..1050.0) {
                        phaseStartTimeMs = now
                        _phase.value = PreItvPhase.IdleCapture(remainingSeconds = 10)
                    } else {
                        _phase.value = PreItvPhase.IdleStabilization(progressPct = progress)
                    }
                }
            }

            is PreItvPhase.IdleCapture -> {
                if (rpm != null) idleRpmList.add(rpm)
                if (ectC != null) idleEctList.add(ectC)
                if (stftPct != null) idleStftList.add(stftPct)
                if (ltftPct != null) idleLtftList.add(ltftPct)
                if (lambda != null) idleLambdaList.add(lambda)

                val elapsed = ((now - phaseStartTimeMs) / 1000).toInt()
                val remaining = (10 - elapsed).coerceAtLeast(0)
                if (remaining == 0) {
                    phaseStartTimeMs = now
                    accelSamples.clear()
                    _phase.value = PreItvPhase.AcceleratedStabilization(currentRpm = rpm ?: 0.0)
                } else {
                    _phase.value = PreItvPhase.IdleCapture(remainingSeconds = remaining)
                }
            }

            is PreItvPhase.AcceleratedStabilization -> {
                val currentRpm = rpm ?: 0.0
                accelSamples.add(now to currentRpm)
                if (accelSamples.size > 30) accelSamples.removeAt(0)
                val stability = stabilityDetector.evaluate(accelSamples)

                if (stability.stable && currentRpm in 2350.0..2650.0) {
                    phaseStartTimeMs = now
                    _phase.value = PreItvPhase.AcceleratedCapture(remainingSeconds = 10, currentRpm = currentRpm)
                } else {
                    _phase.value = PreItvPhase.AcceleratedStabilization(currentRpm = currentRpm)
                }
            }

            is PreItvPhase.AcceleratedCapture -> {
                val currentRpm = rpm ?: 0.0
                accelRpmList.add(currentRpm)
                if (ectC != null) accelEctList.add(ectC)
                if (stftPct != null) accelStftList.add(stftPct)
                if (ltftPct != null) accelLtftList.add(ltftPct)
                if (lambda != null) accelLambdaList.add(lambda)

                val elapsed = ((now - phaseStartTimeMs) / 1000).toInt()
                val remaining = (10 - elapsed).coerceAtLeast(0)
                if (remaining == 0) {
                    _phase.value = PreItvPhase.Analysis
                    computeFinalResult()
                } else {
                    _phase.value = PreItvPhase.AcceleratedCapture(remainingSeconds = remaining, currentRpm = currentRpm)
                }
            }

            PreItvPhase.Analysis -> {}
            is PreItvPhase.Completed -> {}
            is PreItvPhase.Aborted -> {}
        }
    }

    private fun computeFinalResult() {
        val ruleSet = CostaRicaGasolineRules.resolveRuleSet(activeProfile)

        // 1. Idle Evaluation
        val idleRpmMean = if (idleRpmList.isNotEmpty()) idleRpmList.average() else 750.0
        val idleEctMean = if (idleEctList.isNotEmpty()) idleEctList.average() else 90.0
        val idleStft = if (idleStftList.isNotEmpty()) idleStftList.average() else 0.0
        val idleLtft = if (idleLtftList.isNotEmpty()) idleLtftList.average() else 0.0
        val idleLambda = if (idleLambdaList.isNotEmpty()) idleLambdaList.average() else null

        val idleAssessment = combustionEstimator.estimate(
            stftPct = idleStft,
            ltftPct = idleLtft,
            lambda = idleLambda,
            coolantC = idleEctMean,
            isAcceleratedRpm = false
        )

        val idleCoLimit = ruleSet.idleLimits.find { it.metric == GasMetric.CO }
        val idleHcLimit = ruleSet.idleLimits.find { it.metric == GasMetric.HC }
        val idleCo2Limit = ruleSet.idleLimits.find { it.metric == GasMetric.CO2 }

        val idleCoEval = idleCoLimit?.evaluate(idleAssessment.coEstimate) ?: Evaluation.NOT_APPLICABLE
        val idleHcEval = idleHcLimit?.evaluate(idleAssessment.hcEstimate) ?: Evaluation.NOT_APPLICABLE
        val idleCo2Eval = idleCo2Limit?.evaluate(idleAssessment.co2Estimate) ?: Evaluation.NOT_APPLICABLE

        val idleMeasurement = PhaseMeasurement(
            phaseName = "RALENTÍ",
            rpmMean = idleRpmMean,
            ectMean = idleEctMean,
            coEstimate = idleAssessment.coEstimate,
            hcEstimate = idleAssessment.hcEstimate,
            co2Estimate = idleAssessment.co2Estimate,
            coEvaluation = idleCoEval,
            hcEvaluation = idleHcEval,
            co2Evaluation = idleCo2Eval,
            lambdaEstimate = idleLambda
        )

        // 2. Accelerated Evaluation
        val accelRpmMean = if (accelRpmList.isNotEmpty()) accelRpmList.average() else 2500.0
        val accelEctMean = if (accelEctList.isNotEmpty()) accelEctList.average() else 92.0
        val accelStft = if (accelStftList.isNotEmpty()) accelStftList.average() else 0.0
        val accelLtft = if (accelLtftList.isNotEmpty()) accelLtftList.average() else 0.0
        val accelLambda = if (accelLambdaList.isNotEmpty()) accelLambdaList.average() else null

        val accelAssessment = combustionEstimator.estimate(
            stftPct = accelStft,
            ltftPct = accelLtft,
            lambda = accelLambda,
            coolantC = accelEctMean,
            isAcceleratedRpm = true
        )

        val accelCoLimit = ruleSet.acceleratedLimits.find { it.metric == GasMetric.CO }
        val accelHcLimit = ruleSet.acceleratedLimits.find { it.metric == GasMetric.HC }
        val accelCo2Limit = ruleSet.acceleratedLimits.find { it.metric == GasMetric.CO2 }
        val accelLambdaLimit = ruleSet.acceleratedLimits.find { it.metric == GasMetric.LAMBDA }

        val accelCoEval = accelCoLimit?.evaluate(accelAssessment.coEstimate) ?: Evaluation.NOT_APPLICABLE
        val accelHcEval = accelHcLimit?.evaluate(accelAssessment.hcEstimate) ?: Evaluation.NOT_APPLICABLE
        val accelCo2Eval = accelCo2Limit?.evaluate(accelAssessment.co2Estimate) ?: Evaluation.NOT_APPLICABLE
        val accelLambdaEval = if (accelLambda != null && accelLambdaLimit != null) {
            accelLambdaLimit.evaluatePoint(accelLambda)
        } else Evaluation.NOT_APPLICABLE

        val accelMeasurement = PhaseMeasurement(
            phaseName = "ACELERADO_2500",
            rpmMean = accelRpmMean,
            ectMean = accelEctMean,
            coEstimate = accelAssessment.coEstimate,
            hcEstimate = accelAssessment.hcEstimate,
            co2Estimate = accelAssessment.co2Estimate,
            coEvaluation = accelCoEval,
            hcEvaluation = accelHcEval,
            co2Evaluation = accelCo2Eval,
            lambdaEstimate = accelLambda,
            lambdaEvaluation = accelLambdaEval
        )

        // 3. Overall Verdict Synthesis
        val anyFail = idleCoEval == Evaluation.FAIL || idleHcEval == Evaluation.FAIL ||
            accelCoEval == Evaluation.FAIL || accelHcEval == Evaluation.FAIL ||
            accelLambdaEval == Evaluation.FAIL

        val anyInconclusive = idleCoEval == Evaluation.INCONCLUSIVE || idleHcEval == Evaluation.INCONCLUSIVE ||
            accelCoEval == Evaluation.INCONCLUSIVE || accelHcEval == Evaluation.INCONCLUSIVE

        val overallVerdict = when {
            anyFail -> PreItvVerdict.HIGH_RISK
            anyInconclusive -> PreItvVerdict.ELEVATED_RISK
            idleCoEval == Evaluation.PASS && idleHcEval == Evaluation.PASS &&
                accelCoEval == Evaluation.PASS && accelHcEval == Evaluation.PASS -> PreItvVerdict.LOW_RISK
            else -> PreItvVerdict.INCONCLUSIVE
        }

        val explanations = mutableListOf<String>()
        explanations.addAll(idleAssessment.causalExplanations)
        explanations.addAll(accelAssessment.causalExplanations)

        val m06FailedCount = mode06Results.count { it.verdict == Mode06Verdict.FAIL }
        if (m06FailedCount > 0) {
            explanations.add("$m06FailedCount pruebas internas Mode \$06 reprobadas por la ECU")
        }

        val result = PreItvResult(
            jurisdiction = ruleSet.jurisdiction,
            ruleVersion = ruleSet.version,
            idle = idleMeasurement,
            accelerated = accelMeasurement,
            readiness = Evaluation.PASS,
            mode06Evidence = mode06Results,
            overall = overallVerdict,
            confidence = (idleAssessment.confidence + accelAssessment.confidence) / 2.0,
            limitations = listOf(
                "No sustituye la inspección oficial ni el analizador de gases con sonda física.",
                "CO y HC son estimaciones estadísticas derivadas de sensores ECU, fuel trims y física de combustión.",
                "La presencia de fugas en el tubo de escape puede alterar lecturas reales del analizador de gases."
            ),
            causalExplanations = explanations.distinct()
        )

        _phase.value = PreItvPhase.Completed(result)
    }
}
