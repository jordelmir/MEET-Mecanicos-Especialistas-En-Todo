package com.elysium369.meet.core.emissions.preitv

import com.elysium369.meet.core.emissions.analysis.CatalystAssessment
import com.elysium369.meet.core.emissions.analysis.EmissionsEngine
import com.elysium369.meet.core.emissions.analysis.EmissionsEngineInput
import com.elysium369.meet.core.emissions.analysis.OxygenSignalFeatures
import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.emissions.regulations.CostaRicaGasolineRules
import com.elysium369.meet.core.emissions.regulations.GasMetric
import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import com.elysium369.meet.core.obd.ReadinessResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreItvStateMachine(
    private val stabilityDetector: SignalStabilityDetector = SignalStabilityDetector(),
    private val emissionsEngine: EmissionsEngine = EmissionsEngine()
) {

    private val _phase = MutableStateFlow<PreItvPhase>(PreItvPhase.Idle)
    val phase: StateFlow<PreItvPhase> = _phase.asStateFlow()

    private var activeProfile: RegulatoryVehicleProfile = RegulatoryVehicleProfile(modelYear = 2005)
    private var mode06Results: List<Mode06TestResult> = emptyList()
    private var readinessResult: ReadinessResult? = null
    private var o2Features: OxygenSignalFeatures? = null
    private var catalystAssessment: CatalystAssessment? = null
    private var misfireCount: Int = 0
    private var isConnected: Boolean = true

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

    fun setReadinessEvidence(readiness: ReadinessResult?) {
        readinessResult = readiness
    }

    fun setO2Features(features: OxygenSignalFeatures?) {
        o2Features = features
    }

    fun setCatalystAssessment(assessment: CatalystAssessment?) {
        catalystAssessment = assessment
    }

    fun setMisfireCount(count: Int) {
        misfireCount = count
    }

    fun setConnectionState(connected: Boolean) {
        isConnected = connected
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

        // 1. Idle Evaluation via Authoritative EmissionsEngine
        val idleRpmMean = if (idleRpmList.isNotEmpty()) idleRpmList.average() else 750.0
        val idleEctMean = if (idleEctList.isNotEmpty()) idleEctList.average() else 90.0
        val idleStft = if (idleStftList.isNotEmpty()) idleStftList.average() else null
        val idleLtft = if (idleLtftList.isNotEmpty()) idleLtftList.average() else null
        val idleLambda = if (idleLambdaList.isNotEmpty()) idleLambdaList.average() else null

        val idleInput = EmissionsEngineInput(
            rpm = idleRpmMean,
            ectC = idleEctMean,
            stftPct = idleStft,
            ltftPct = idleLtft,
            lambda = idleLambda,
            misfireCount = misfireCount,
            o2UpstreamFeatures = o2Features,
            catalystAssessment = catalystAssessment,
            mode06Results = mode06Results,
            readinessResult = readinessResult,
            activeProfile = activeProfile,
            isAcceleratedRpm = false,
            isConnected = isConnected,
            physicalSampleCount = idleRpmList.size
        )
        val idleOutput = emissionsEngine.evaluate(idleInput)

        val idleMeasurement = PhaseMeasurement(
            phaseName = "RALENTÍ",
            rpmMean = idleRpmMean,
            ectMean = idleEctMean,
            coEstimate = idleOutput.combustionAssessment.coEstimate,
            hcEstimate = idleOutput.combustionAssessment.hcEstimate,
            co2Estimate = idleOutput.combustionAssessment.co2Estimate,
            coEvaluation = idleOutput.coEvaluation,
            hcEvaluation = idleOutput.hcEvaluation,
            co2Evaluation = idleOutput.co2Evaluation,
            lambdaEstimate = idleLambda,
            lambdaEvaluation = idleOutput.lambdaEvaluation
        )

        // 2. Accelerated Evaluation via Authoritative EmissionsEngine
        val accelRpmMean = if (accelRpmList.isNotEmpty()) accelRpmList.average() else 2500.0
        val accelEctMean = if (accelEctList.isNotEmpty()) accelEctList.average() else 92.0
        val accelStft = if (accelStftList.isNotEmpty()) accelStftList.average() else null
        val accelLtft = if (accelLtftList.isNotEmpty()) accelLtftList.average() else null
        val accelLambda = if (accelLambdaList.isNotEmpty()) accelLambdaList.average() else null

        val accelInput = EmissionsEngineInput(
            rpm = accelRpmMean,
            ectC = accelEctMean,
            stftPct = accelStft,
            ltftPct = accelLtft,
            lambda = accelLambda,
            misfireCount = misfireCount,
            o2UpstreamFeatures = o2Features,
            catalystAssessment = catalystAssessment,
            mode06Results = mode06Results,
            readinessResult = readinessResult,
            activeProfile = activeProfile,
            isAcceleratedRpm = true,
            isConnected = isConnected,
            physicalSampleCount = accelRpmList.size
        )
        val accelOutput = emissionsEngine.evaluate(accelInput)

        val accelMeasurement = PhaseMeasurement(
            phaseName = "ACELERADO_2500",
            rpmMean = accelRpmMean,
            ectMean = accelEctMean,
            coEstimate = accelOutput.combustionAssessment.coEstimate,
            hcEstimate = accelOutput.combustionAssessment.hcEstimate,
            co2Estimate = accelOutput.combustionAssessment.co2Estimate,
            coEvaluation = accelOutput.coEvaluation,
            hcEvaluation = accelOutput.hcEvaluation,
            co2Evaluation = accelOutput.co2Evaluation,
            lambdaEstimate = accelLambda,
            lambdaEvaluation = accelOutput.lambdaEvaluation
        )

        // 3. Overall Verdict Synthesis
        val overallVerdict = when {
            idleOutput.overallVerdict == PreItvVerdict.HIGH_RISK || accelOutput.overallVerdict == PreItvVerdict.HIGH_RISK -> PreItvVerdict.HIGH_RISK
            idleOutput.overallVerdict == PreItvVerdict.ELEVATED_RISK || accelOutput.overallVerdict == PreItvVerdict.ELEVATED_RISK -> PreItvVerdict.ELEVATED_RISK
            idleOutput.overallVerdict == PreItvVerdict.LOW_RISK && accelOutput.overallVerdict == PreItvVerdict.LOW_RISK -> PreItvVerdict.LOW_RISK
            else -> PreItvVerdict.INCONCLUSIVE
        }

        val explanations = (idleOutput.causalExplanations + accelOutput.causalExplanations).distinct()

        val result = PreItvResult(
            jurisdiction = ruleSet.jurisdiction,
            ruleVersion = ruleSet.version,
            idle = idleMeasurement,
            accelerated = accelMeasurement,
            readiness = accelOutput.readinessEvaluation,
            mode06Evidence = mode06Results,
            overall = overallVerdict,
            confidence = (idleOutput.combustionAssessment.confidence + accelOutput.combustionAssessment.confidence) / 2.0,
            limitations = listOf(
                "No sustituye la inspección oficial ni el analizador de gases con sonda física.",
                "CO y HC son estimaciones estadísticas derivadas de sensores ECU, fuel trims y física de combustión.",
                "La presencia de fugas en el tubo de escape puede alterar lecturas reales del analizador de gases."
            ),
            causalExplanations = explanations
        )

        _phase.value = PreItvPhase.Completed(result)
    }
}
