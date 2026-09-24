package com.elysium369.meet.core.emissions.analysis

import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict

enum class CatalystAssessmentState {
    NORMAL_EVIDENCE,
    DEGRADED_EVIDENCE,
    STRONG_DEGRADATION_EVIDENCE,
    INCONCLUSIVE
}

data class CatalystAssessment(
    val state: CatalystAssessmentState,
    val isolationRatio: Double?,
    val upstreamCrossCount: Int,
    val downstreamCrossCount: Int,
    val mode06Passed: Boolean?,
    val hasP0420P0430Dtc: Boolean,
    val confidence: Double,
    val explanation: String
)

/**
 * Multi-factor Catalyst Health and Conversion Efficiency Analyzer.
 * Fuses dual-O2 cross-correlation, Mode $06 on-board monitor evidence,
 * misfire history, and active DTCs.
 */
class CatalystEfficiencyAnalyzer {

    fun assess(
        upstreamFeatures: OxygenSignalFeatures?,
        downstreamFeatures: OxygenSignalFeatures?,
        mode06CatalystResults: List<Mode06TestResult>,
        activeDtcs: List<String> = emptyList(),
        misfireDetected: Boolean = false,
        catalystTempC: Double? = null
    ): CatalystAssessment {
        val hasDtc = activeDtcs.any { it.equals("P0420", ignoreCase = true) || it.equals("P0430", ignoreCase = true) }

        val m06AnyFail = mode06CatalystResults.any { it.verdict == Mode06Verdict.FAIL }
        val m06AllPass = mode06CatalystResults.isNotEmpty() && mode06CatalystResults.all { it.verdict == Mode06Verdict.PASS }

        if (upstreamFeatures == null || downstreamFeatures == null ||
            upstreamFeatures.sampleCount < 4 || downstreamFeatures.sampleCount < 4
        ) {
            val state = when {
                hasDtc || m06AnyFail -> CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE
                m06AllPass -> CatalystAssessmentState.NORMAL_EVIDENCE
                else -> CatalystAssessmentState.INCONCLUSIVE
            }
            return CatalystAssessment(
                state = state,
                isolationRatio = null,
                upstreamCrossCount = 0,
                downstreamCrossCount = 0,
                mode06Passed = if (mode06CatalystResults.isNotEmpty()) m06AllPass else null,
                hasP0420P0430Dtc = hasDtc,
                confidence = if (hasDtc || m06AnyFail || m06AllPass) 0.85 else 0.20,
                explanation = if (hasDtc) "DTC de eficiencia catalítica presente (P0420/P0430)"
                else if (m06AnyFail) "Fallo registrado en monitor interno Mode \$06 del catalizador"
                else "Datos de señal O2 insuficientes para correlación temporal"
            )
        }

        val upCross = upstreamFeatures.crossCount
        val downCross = downstreamFeatures.crossCount

        // In a healthy converter, downstream O2 is buffered and quiet (low cross count).
        // Isolation ratio = downstream crosses / upstream crosses.
        // Good: ratio < 0.25 (downstream rarely switches)
        // Degraded: ratio between 0.30 and 0.60
        // Severely degraded / failed: ratio > 0.60 (downstream tracks upstream closely)
        val isolationRatio = if (upCross >= 4) {
            downCross.toDouble() / upCross.toDouble()
        } else null

        val reasons = mutableListOf<String>()
        var degradationScore = 0.0

        if (hasDtc) {
            degradationScore += 0.50
            reasons.add("Código P0420/P0430 activo")
        }

        if (m06AnyFail) {
            degradationScore += 0.40
            reasons.add("Monitor Mode \$06 reprobado")
        } else if (m06AllPass) {
            degradationScore -= 0.30
            reasons.add("Monitor Mode \$06 aprobado")
        }

        if (isolationRatio != null) {
            when {
                isolationRatio > 0.65 -> {
                    degradationScore += 0.45
                    reasons.add("B1S2 oscila al ritmo de B1S1 (aislamiento deficiente, ratio=${String.format("%.2f", isolationRatio)})")
                }
                isolationRatio > 0.35 -> {
                    degradationScore += 0.20
                    reasons.add("B1S2 muestra actividad moderada (ratio=${String.format("%.2f", isolationRatio)})")
                }
                else -> {
                    degradationScore -= 0.25
                    reasons.add("B1S2 adecuadamente aislado y amortiguado (ratio=${String.format("%.2f", isolationRatio)})")
                }
            }
        }

        if (misfireDetected) {
            degradationScore += 0.15
            reasons.add("Fallas de encendido (misfire) activas pueden sobrecalentar o envenenar el catalizador")
        }

        if (catalystTempC != null && catalystTempC < 300.0) {
            reasons.add("Temperatura del catalizador (${catalystTempC.toInt()}°C) por debajo del punto de encendido térmico (light-off ~350°C)")
        }

        val state = when {
            degradationScore >= 0.55 -> CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE
            degradationScore >= 0.25 -> CatalystAssessmentState.DEGRADED_EVIDENCE
            degradationScore <= -0.10 && isolationRatio != null && isolationRatio < 0.35 -> CatalystAssessmentState.NORMAL_EVIDENCE
            else -> CatalystAssessmentState.INCONCLUSIVE
        }

        val confidence = when {
            hasDtc && m06AnyFail -> 0.95
            isolationRatio != null && (m06AllPass || m06AnyFail) -> 0.90
            isolationRatio != null -> 0.75
            else -> 0.40
        }

        return CatalystAssessment(
            state = state,
            isolationRatio = isolationRatio,
            upstreamCrossCount = upCross,
            downstreamCrossCount = downCross,
            mode06Passed = if (mode06CatalystResults.isNotEmpty()) m06AllPass else null,
            hasP0420P0430Dtc = hasDtc,
            confidence = confidence,
            explanation = reasons.joinToString(". ")
        )
    }
}
