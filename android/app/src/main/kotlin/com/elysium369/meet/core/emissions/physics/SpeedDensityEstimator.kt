package com.elysium369.meet.core.emissions.physics

import kotlin.math.max

sealed interface Estimate<out T> {
    data class Available<T>(
        val value: T,
        val uncertaintyPct: Double,
        val source: String
    ) : Estimate<T>

    data class Unavailable(
        val reason: String
    ) : Estimate<Nothing>
}

/**
 * Thermodynamic Speed-Density Estimator.
 * Calculates intake air mass flow:
 *   m_dot_air = (MAP * V_d * RPM * VE) / (120 * R * T)
 *
 * Strict Requirement:
 * Requires confirmed displacement. Never guesses an engine displacement.
 */
class SpeedDensityEstimator {

    companion object {
        const val R_AIR_SPECIFIC = 0.287058 // kJ / (kg * K)
        const val KELVIN_OFFSET = 273.15
    }

    fun estimate(
        displacementLiters: Double?,
        rpm: Double?,
        mapKpa: Double?,
        iatC: Double?,
        volumetricEfficiency: Double? = null
    ): Estimate<Double> {
        if (displacementLiters == null || displacementLiters <= 0.0) {
            return Estimate.Unavailable(reason = "ENGINE_DISPLACEMENT_REQUIRED")
        }
        if (rpm == null || rpm <= 0.0) {
            return Estimate.Unavailable(reason = "RPM_REQUIRED")
        }
        if (mapKpa == null || mapKpa <= 0.0) {
            return Estimate.Unavailable(reason = "MAP_REQUIRED")
        }
        if (iatC == null) {
            return Estimate.Unavailable(reason = "IAT_REQUIRED")
        }

        val tempK = iatC + KELVIN_OFFSET
        if (tempK <= 0.0) {
            return Estimate.Unavailable(reason = "INVALID_TEMPERATURE")
        }

        // Default calibrated VE curve if not explicitly supplied
        // At idle/light load: ~0.75; at mid/high load: ~0.85-0.92
        val ve = volumetricEfficiency ?: 0.82

        // Formula: (MAP[kPa] * V_d[L] * RPM * VE) / (120 * R * T[K])
        // Yields air mass flow in grams per second (g/s)
        val denominator = 120.0 * R_AIR_SPECIFIC * tempK
        val airMassFlowGps = (mapKpa * displacementLiters * rpm * ve) / denominator

        return Estimate.Available(
            value = max(0.0, airMassFlowGps),
            uncertaintyPct = if (volumetricEfficiency != null) 5.0 else 12.0,
            source = "SPEED_DENSITY_ESTIMATE"
        )
    }
}
