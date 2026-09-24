package com.elysium369.meet.core.emissions.physics

import com.elysium369.meet.core.emissions.domain.EvidenceOrigin
import com.elysium369.meet.core.emissions.domain.TruthClass
import com.elysium369.meet.core.emissions.domain.EngineProfile

sealed interface FuelRateResult {
    data class Available(
        val litersPerHour: Double,
        val gramsPerSecond: Double,
        val origin: EvidenceOrigin,
        val truthClass: TruthClass,
        val uncertaintyPct: Double,
        val lineage: Set<String>
    ) : FuelRateResult

    data class Unavailable(
        val reason: String
    ) : FuelRateResult
}

/**
 * Fuel Rate Estimator enforcing strict hierarchy of evidence:
 * 1. Physical ECU PID 015E (Engine Fuel Rate) -> ECU_DIRECT, MEASURED
 * 2. OEM Telemetry broadcast -> ECU_DIRECT, REPORTED
 * 3. Physical MAF + independently measured wideband Lambda -> PHYSICS_DERIVED, DERIVED
 * 4. Physical MAF + Closed-Loop assumption (Lambda = 1.0) -> MODEL_ESTIMATED, ESTIMATED
 * 5. Speed-Density air mass + confirmed displacement -> MODEL_ESTIMATED, ESTIMATED
 * 6. Unavailable (never guesses without physical signals)
 */
class FuelRateEstimator(
    private val speedDensityEstimator: SpeedDensityEstimator = SpeedDensityEstimator()
) {

    fun estimate(
        ecuFuelRateLph: Double?,
        mafGps: Double?,
        measuredLambda: Double?,
        isClosedLoop: Boolean,
        engineProfile: EngineProfile,
        mapKpa: Double? = null,
        rpm: Double? = null,
        iatC: Double? = null
    ): FuelRateResult {
        val fuelDensity = engineProfile.fuelDensityKgPerLiter
        val stoichAfr = engineProfile.stoichAfr

        // 1. Level 1: ECU physical PID 015E
        if (ecuFuelRateLph != null && ecuFuelRateLph > 0.0) {
            val gps = (ecuFuelRateLph * fuelDensity * 1000.0) / 3600.0
            return FuelRateResult.Available(
                litersPerHour = ecuFuelRateLph,
                gramsPerSecond = gps,
                origin = EvidenceOrigin.ECU_DIRECT,
                truthClass = TruthClass.MEASURED,
                uncertaintyPct = 2.0,
                lineage = setOf("PID_015E")
            )
        }

        // 2. Level 2: Physical MAF + measured wideband Lambda
        if (mafGps != null && mafGps > 0.0 && measuredLambda != null && measuredLambda > 0.0) {
            val afr = stoichAfr * measuredLambda
            val fuelGps = mafGps / afr
            val lph = (fuelGps * 3600.0) / (fuelDensity * 1000.0)
            return FuelRateResult.Available(
                litersPerHour = lph,
                gramsPerSecond = fuelGps,
                origin = EvidenceOrigin.PHYSICS_DERIVED,
                truthClass = TruthClass.DERIVED,
                uncertaintyPct = 4.0,
                lineage = setOf("MAF_PHYSICAL", "MEASURED_LAMBDA")
            )
        }

        // 3. Level 3: Physical MAF + Closed-Loop Assumption (Stoichiometric Lambda = 1.0)
        if (mafGps != null && mafGps > 0.0 && isClosedLoop) {
            val fuelGps = mafGps / stoichAfr
            val lph = (fuelGps * 3600.0) / (fuelDensity * 1000.0)
            return FuelRateResult.Available(
                litersPerHour = lph,
                gramsPerSecond = fuelGps,
                origin = EvidenceOrigin.MODEL_ESTIMATED,
                truthClass = TruthClass.ESTIMATED,
                uncertaintyPct = 7.0,
                lineage = setOf("MAF_PHYSICAL", "ASSUMED_STOICHIOMETRIC_LAMBDA_CLOSED_LOOP")
            )
        }

        // 4. Level 4: Speed-Density derived air mass
        if (mapKpa != null && rpm != null && iatC != null) {
            val sdEstimate = speedDensityEstimator.estimate(
                displacementLiters = engineProfile.displacementLiters,
                rpm = rpm,
                mapKpa = mapKpa,
                iatC = iatC
            )
            if (sdEstimate is Estimate.Available) {
                val estAirGps = sdEstimate.value
                val effLambda = if (measuredLambda != null && measuredLambda > 0.0) measuredLambda else 1.0
                val fuelGps = estAirGps / (stoichAfr * effLambda)
                val lph = (fuelGps * 3600.0) / (fuelDensity * 1000.0)
                val lineageSet = if (measuredLambda != null) {
                    setOf("SPEED_DENSITY_AIR", "MEASURED_LAMBDA")
                } else {
                    setOf("SPEED_DENSITY_AIR", "ASSUMED_STOICHIOMETRIC_LAMBDA")
                }
                return FuelRateResult.Available(
                    litersPerHour = lph,
                    gramsPerSecond = fuelGps,
                    origin = EvidenceOrigin.MODEL_ESTIMATED,
                    truthClass = TruthClass.ESTIMATED,
                    uncertaintyPct = 14.0,
                    lineage = lineageSet
                )
            } else if (sdEstimate is Estimate.Unavailable) {
                return FuelRateResult.Unavailable(reason = sdEstimate.reason)
            }
        }

        return FuelRateResult.Unavailable(reason = "INSUFFICIENT_TELEMETRY_FOR_FUEL_RATE")
    }
}
