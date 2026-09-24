package com.elysium369.meet.core.emissions.analysis

enum class CombustionRiskLevel {
    LOW,
    MODERATE,
    HIGH,
    INCONCLUSIVE
}

data class GasEstimate(
    val pointEstimate: Double,
    val lower95: Double,
    val upper95: Double,
    val unit: String,
    val modelVersion: String = "VANGUARD_VIRTUAL_GAS_1.0"
)

data class CombustionAssessment(
    val coRisk: CombustionRiskLevel,
    val hcRisk: CombustionRiskLevel,
    val coEstimate: GasEstimate,
    val hcEstimate: GasEstimate,
    val co2Estimate: GasEstimate,
    val combinedTrimPct: Double?,
    val misfireCount: Int,
    val confidence: Double,
    val causalExplanations: List<String>,
    val disclaimer: String = "NO ES UNA INSPECCIÓN OFICIAL. CO y HC son estimaciones del modelo basadas en telemetría OBD."
)

/**
 * Multi-variable Combustion State & Emissions Risk Estimator.
 * Synthesizes fuel trims, lambda evidence, oxygen sensor switching,
 * misfire count, and catalyst degradation to infer raw emission risks.
 */
class CombustionRiskEstimator {

    fun estimate(
        stftPct: Double?,
        ltftPct: Double?,
        lambda: Double?,
        coolantC: Double?,
        misfireCount: Int = 0,
        o2UpstreamFeatures: OxygenSignalFeatures? = null,
        catalystAssessment: CatalystAssessment? = null,
        isAcceleratedRpm: Boolean = false
    ): CombustionAssessment {
        val combinedTrim = if (stftPct != null && ltftPct != null) stftPct + ltftPct else null
        val explanations = mutableListOf<String>()

        var coScore = 0.0 // higher means worse CO
        var hcScore = 0.0 // higher means worse HC

        // 1. Coolant Temperature
        if (coolantC != null && coolantC < 75.0) {
            coScore += 0.35
            hcScore += 0.40
            explanations.add("Motor en fase de calentamiento (${coolantC.toInt()}°C): enriquecimiento en frío activo")
        }

        // 2. Misfires
        if (misfireCount > 0) {
            hcScore += (misfireCount * 0.15).coerceAtMost(0.80)
            explanations.add("$misfireCount falla(s) de encendido (misfire): combustible sin quemar expulsado al escape (HC elevado)")
        }

        // 3. Fuel Trims & Lambda
        if (combinedTrim != null) {
            when {
                combinedTrim > 12.0 -> {
                    // ECU compensating for lean condition (vacuum leak, dirty MAF, low fuel pressure)
                    hcScore += 0.30
                    explanations.add("Corrección de combustible elevada (+${String.format("%.1f", combinedTrim)}%): compensación por mezcla pobre")
                }
                combinedTrim < -12.0 -> {
                    // ECU pulling fuel -> rich condition
                    coScore += 0.45
                    hcScore += 0.25
                    explanations.add("Corrección de combustible muy negativa (${String.format("%.1f", combinedTrim)}%): exceso de combustible no quemado (CO elevado)")
                }
            }
        }

        // 4. Lambda
        if (lambda != null) {
            if (lambda < 0.95) {
                coScore += 0.40
                explanations.add("Mezcla rica detectada (Lambda = ${String.format("%.3f", lambda)} < 1.0): combustión deficiente en oxígeno, genera CO")
            } else if (lambda > 1.10) {
                hcScore += 0.30
                explanations.add("Exceso de oxígeno en escape (Lambda = ${String.format("%.3f", lambda)} > 1.0): posible fuga en escape o combustión incompleta")
            }
        }

        // 5. Catalyst Assessment
        if (catalystAssessment != null) {
            when (catalystAssessment.state) {
                CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE -> {
                    coScore += 0.40
                    hcScore += 0.45
                    explanations.add("Catalizador con evidencia severa de degradación: baja conversión de CO y HC")
                }
                CatalystAssessmentState.DEGRADED_EVIDENCE -> {
                    coScore += 0.20
                    hcScore += 0.25
                    explanations.add("Catalizador con eficiencia reducida")
                }
                CatalystAssessmentState.NORMAL_EVIDENCE -> {
                    coScore -= 0.15
                    hcScore -= 0.15
                }
                CatalystAssessmentState.INCONCLUSIVE -> {}
            }
        }

        // 6. Upstream O2 Waveform
        if (o2UpstreamFeatures != null && o2UpstreamFeatures.switchHz != null) {
            if (o2UpstreamFeatures.switchHz < 0.5) {
                coScore += 0.20
                hcScore += 0.20
                explanations.add("Sensor de O2 con conmutación lenta (${String.format("%.2f", o2UpstreamFeatures.switchHz)} Hz): ciclo cerrado tardío")
            }
        }

        val coRisk = when {
            coScore >= 0.55 -> CombustionRiskLevel.HIGH
            coScore >= 0.25 -> CombustionRiskLevel.MODERATE
            coScore <= 0.10 && combinedTrim != null -> CombustionRiskLevel.LOW
            else -> CombustionRiskLevel.INCONCLUSIVE
        }

        val hcRisk = when {
            hcScore >= 0.55 -> CombustionRiskLevel.HIGH
            hcScore >= 0.25 -> CombustionRiskLevel.MODERATE
            hcScore <= 0.10 && combinedTrim != null -> CombustionRiskLevel.LOW
            else -> CombustionRiskLevel.INCONCLUSIVE
        }

        // Calibrated model estimation curves based on physical domain anchors
        // Baseline for standard healthy gasoline engine:
        // Idle: CO ~ 0.10%, HC ~ 45 ppm, CO2 ~ 14.5%
        // Accel: CO ~ 0.08%, HC ~ 30 ppm, CO2 ~ 14.8%
        val baseCo = if (isAcceleratedRpm) 0.12 else 0.15
        val baseHc = if (isAcceleratedRpm) 50.0 else 65.0
        val baseCo2 = if (isAcceleratedRpm) 14.5 else 14.0

        val estCo = (baseCo + coScore * 0.70).coerceIn(0.05, 4.5)
        val coLow = (estCo * 0.75).coerceAtLeast(0.01)
        val coHigh = (estCo * 1.35).coerceAtLeast(coLow + 0.05)

        val estHc = (baseHc + hcScore * 350.0).coerceIn(15.0, 800.0)
        val hcLow = (estHc * 0.70).coerceAtLeast(5.0)
        val hcHigh = (estHc * 1.40).coerceAtLeast(hcLow + 15.0)

        val estCo2 = (baseCo2 - (coScore + hcScore) * 1.5).coerceIn(9.0, 15.5)
        val co2Low = (estCo2 - 0.7).coerceAtLeast(5.0)
        val co2High = (estCo2 + 0.7).coerceAtMost(16.0)

        val confidence = when {
            combinedTrim != null && o2UpstreamFeatures != null && catalystAssessment != null -> 0.86
            combinedTrim != null -> 0.72
            else -> 0.45
        }

        return CombustionAssessment(
            coRisk = coRisk,
            hcRisk = hcRisk,
            coEstimate = GasEstimate(estCo, coLow, coHigh, "% vol"),
            hcEstimate = GasEstimate(estHc, hcLow, hcHigh, "ppm"),
            co2Estimate = GasEstimate(estCo2, co2Low, co2High, "% vol"),
            combinedTrimPct = combinedTrim,
            misfireCount = misfireCount,
            confidence = confidence,
            causalExplanations = explanations
        )
    }
}
