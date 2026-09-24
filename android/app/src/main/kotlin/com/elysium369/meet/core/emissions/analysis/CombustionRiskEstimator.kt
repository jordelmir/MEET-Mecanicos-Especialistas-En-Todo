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

        // 3. Fuel Trims (Continuous proportional scaling)
        if (combinedTrim != null) {
            when {
                combinedTrim < -5.0 -> {
                    // ECU removing fuel -> rich condition sensed by upstream O2
                    val richDrift = ((-combinedTrim - 5.0) / 12.0).coerceIn(0.1, 1.2)
                    coScore += (richDrift * 0.50)
                    hcScore += (richDrift * 0.30)
                    explanations.add("Corrección de combustible negativa (${String.format("%.1f", combinedTrim)}%): ECU detecta mezcla rica y reduce inyección")
                }
                combinedTrim > 8.0 -> {
                    // ECU adding fuel -> lean condition (vacuum leak, weak fuel pump)
                    val leanDrift = ((combinedTrim - 8.0) / 12.0).coerceIn(0.1, 1.2)
                    hcScore += (leanDrift * 0.45)
                    explanations.add("Corrección de combustible elevada (+${String.format("%.1f", combinedTrim)}%): compensación por mezcla pobre (fuga de vacío o baja presión)")
                }
            }
        }

        // 4. Lambda & Exhaust Leak Correlation
        if (lambda != null) {
            when {
                lambda < 0.95 -> {
                    val richDelta = ((0.98 - lambda) / 0.15).coerceIn(0.1, 1.0)
                    coScore += (richDelta * 0.45)
                    explanations.add("Mezcla rica detectada (Lambda = ${String.format("%.3f", lambda)} < 1.0): exceso de combustible relativo al aire")
                }
                lambda > 1.05 -> {
                    val leanDelta = ((lambda - 1.03) / 0.20).coerceIn(0.1, 1.5)
                    hcScore += (leanDelta * 0.55)
                    if (combinedTrim != null && combinedTrim < -5.0) {
                        // High Lambda + Negative Trims: Classic exhaust leak allowing ambient air ingress
                        hcScore += 0.35
                        coScore += 0.15
                        explanations.add("Lambda alta (${String.format("%.3f", lambda)}) con trims negativos (${String.format("%.1f", combinedTrim)}%): evidencia fuerte de fuga en tubo de escape (ingreso de aire) o fallo de encendido")
                    } else {
                        explanations.add("Exceso de oxígeno en escape (Lambda = ${String.format("%.3f", lambda)} > 1.0): mezcla pobre o dilución en escape")
                    }
                }
            }
        }

        // 5. Catalyst Assessment
        if (catalystAssessment != null) {
            when (catalystAssessment.state) {
                CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE -> {
                    coScore += 0.45
                    hcScore += 0.55
                    explanations.add("Catalizador con evidencia severa de degradación: baja conversión de CO y HC")
                }
                CatalystAssessmentState.DEGRADED_EVIDENCE -> {
                    coScore += 0.25
                    hcScore += 0.35
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
            coScore >= 0.50 -> CombustionRiskLevel.HIGH
            coScore >= 0.20 -> CombustionRiskLevel.MODERATE
            coScore <= 0.10 && combinedTrim != null -> CombustionRiskLevel.LOW
            else -> CombustionRiskLevel.INCONCLUSIVE
        }

        val hcRisk = when {
            hcScore >= 0.50 -> CombustionRiskLevel.HIGH
            hcScore >= 0.20 -> CombustionRiskLevel.MODERATE
            hcScore <= 0.10 && combinedTrim != null -> CombustionRiskLevel.LOW
            else -> CombustionRiskLevel.INCONCLUSIVE
        }

        // Baseline for standard healthy gasoline engine:
        val baseCo = if (isAcceleratedRpm) 0.10 else 0.12
        val baseHc = if (isAcceleratedRpm) 40.0 else 55.0
        val baseCo2 = if (isAcceleratedRpm) 14.5 else 14.0

        val estCo = (baseCo + coScore * 0.75).coerceIn(0.05, 4.5)
        val coLow = (estCo * 0.75).coerceAtLeast(0.01)
        val coHigh = (estCo * 1.35).coerceAtLeast(coLow + 0.05)

        val estHc = (baseHc + hcScore * 480.0).coerceIn(15.0, 950.0)
        val hcLow = (estHc * 0.70).coerceAtLeast(5.0)
        val hcHigh = (estHc * 1.40).coerceAtLeast(hcLow + 15.0)

        val estCo2 = (baseCo2 - (coScore + hcScore) * 1.6).coerceIn(8.5, 15.5)
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
