package com.elysium369.meet.core.emissions.regulations

import com.elysium369.meet.core.emissions.domain.EngineCycle
import com.elysium369.meet.core.emissions.domain.Evaluation
import com.elysium369.meet.core.emissions.domain.FuelType
import com.elysium369.meet.core.emissions.analysis.GasEstimate

enum class GasMetric {
    CO,
    HC,
    CO2,
    O2,
    NOX,
    LAMBDA
}

data class EmissionLimit(
    val metric: GasMetric,
    val min: Double? = null,
    val max: Double? = null,
    val unit: String
) {
    /**
     * Conservative evaluation rule:
     * - For upper bound (max limit L):
     *     upper95 < L -> PASS
     *     lower95 > L -> FAIL
     *     else -> INCONCLUSIVE
     * - For lower bound (min limit L):
     *     lower95 >= L -> PASS
     *     upper95 < L -> FAIL
     *     else -> INCONCLUSIVE
     */
    fun evaluate(estimate: GasEstimate): Evaluation {
        val maxVal = max
        val minVal = min

        if (maxVal != null) {
            return when {
                estimate.upper95 < maxVal -> Evaluation.PASS
                estimate.lower95 > maxVal -> Evaluation.FAIL
                else -> Evaluation.INCONCLUSIVE
            }
        }

        if (minVal != null) {
            return when {
                estimate.lower95 >= minVal -> Evaluation.PASS
                estimate.upper95 < minVal -> Evaluation.FAIL
                else -> Evaluation.INCONCLUSIVE
            }
        }

        return Evaluation.NOT_APPLICABLE
    }

    /**
     * Direct point-value evaluation (used for physical gas probe measurements or exact test sheet verification).
     */
    fun evaluatePoint(value: Double): Evaluation {
        val maxVal = max
        val minVal = min
        if (maxVal != null && value > maxVal) return Evaluation.FAIL
        if (minVal != null && value < minVal) return Evaluation.FAIL
        return Evaluation.PASS
    }
}

data class EmissionRuleSet(
    val jurisdiction: String,
    val version: String,
    val effectiveFrom: String,
    val sourceReference: String,
    val idleLimits: List<EmissionLimit>,
    val acceleratedLimits: List<EmissionLimit>
)

data class RegulatoryVehicleProfile(
    val modelYear: Int?,
    val firstRegistrationDate: String? = null,
    val costaRicaEntryDate: String? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
    val engineCycle: EngineCycle = EngineCycle.FOUR_STROKE
)
