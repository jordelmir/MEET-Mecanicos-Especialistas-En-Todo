package com.elysium369.meet.core.emissions.physics

import com.elysium369.meet.core.emissions.domain.EmissionMetric
import com.elysium369.meet.core.emissions.domain.EvidenceOrigin
import com.elysium369.meet.core.emissions.domain.TruthClass
import com.elysium369.meet.core.emissions.domain.EngineProfile

data class CO2Outputs(
    val massRateGps: Double,
    val massRateGph: Double,
    val gramsPerKm: Double?,
    val volPercentEstimate: EmissionMetric
)

/**
 * Mass-Conservation Physics Model for Carbon Dioxide (CO2).
 * Distinguishes strictly between:
 * - CO2 mass rate [g/s, g/h] (Physics Derived via carbon mass balance)
 * - CO2 distance rate [g/km] (Physics Derived when speed > 5 km/h)
 * - CO2 volume percent [% vol] (Model Estimated dry exhaust fraction, NOT measured)
 */
class CO2PhysicsModel {

    companion object {
        const val CO2_TO_C_MOLAR_RATIO = 44.01 / 12.011 // ~3.664
        const val COMPLETE_COMBUSTION_MAX_CO2_PCT = 15.2 // % dry vol for typical E0/E10 gasoline
    }

    fun calculate(
        fuelRateGps: Double?,
        speedKmh: Double?,
        lambda: Double?,
        catalystOxidationEfficiency: Double? = 0.985,
        engineProfile: EngineProfile = EngineProfile()
    ): CO2Outputs? {
        if (fuelRateGps == null || fuelRateGps <= 0.0) return null

        val wC = engineProfile.carbonMassFraction
        val etaOx = (catalystOxidationEfficiency ?: 0.985).coerceIn(0.50, 0.999)

        // Mass rate: m_dot_CO2 = m_dot_fuel * w_C * (44 / 12) * eta_ox
        val co2Gps = fuelRateGps * wC * CO2_TO_C_MOLAR_RATIO * etaOx
        val co2Gph = co2Gps * 3600.0

        // Distance rate: g/km (only valid when vehicle is in motion)
        val gPerKm = if (speedKmh != null && speedKmh >= 5.0) {
            (co2Gps * 3600.0) / speedKmh
        } else null

        // Volume percent (% vol):
        // In dry exhaust gas, CO2% peaks at ~15.2% near stoichiometric lambda.
        // As lambda exceeds 1.0 (lean), excess air dilutes exhaust.
        // If rich (lambda < 1.0), CO and HC increase while CO2 drops.
        val effLambda = (lambda ?: 1.0).coerceIn(0.7, 1.8)
        val lambdaFactor = if (effLambda >= 1.0) {
            1.0 / effLambda
        } else {
            1.0 - (1.0 - effLambda) * 1.5
        }
        val estimatedVolPct = (COMPLETE_COMBUSTION_MAX_CO2_PCT * lambdaFactor * etaOx).coerceIn(5.0, 15.5)

        val volMetric = EmissionMetric.estimated(
            id = "CO2_VOL_PCT",
            value = estimatedVolPct,
            unit = "% vol",
            lower95 = (estimatedVolPct - 0.8).coerceAtLeast(0.0),
            upper95 = (estimatedVolPct + 0.8).coerceAtMost(16.0),
            lineage = setOf("FUEL_RATE_GPS", "CARBON_BALANCE", "EXHAUST_DILUTION_MODEL"),
            quality = 0.85
        )

        return CO2Outputs(
            massRateGps = co2Gps,
            massRateGph = co2Gph,
            gramsPerKm = gPerKm,
            volPercentEstimate = volMetric
        )
    }
}
