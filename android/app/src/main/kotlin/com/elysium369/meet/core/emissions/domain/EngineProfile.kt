package com.elysium369.meet.core.emissions.domain

/**
 * Verified engine physical parameters.
 * Eliminates arbitrary global constants (e.g. 1.6L) by requiring verified origin.
 */
data class EngineProfile(
    val displacementLiters: Double? = null,
    val cylinderCount: Int? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
    val stoichAfr: Double = 14.7,
    val fuelDensityKgPerLiter: Double = 0.745,
    val carbonMassFraction: Double = 0.855,
    val source: String = "UNVERIFIED"
) {
    companion object {
        fun standardGasoline(displacementL: Double?, cylinders: Int? = 4): EngineProfile {
            return EngineProfile(
                displacementLiters = displacementL,
                cylinderCount = cylinders,
                fuelType = FuelType.GASOLINE,
                stoichAfr = 14.7,
                fuelDensityKgPerLiter = 0.745,
                carbonMassFraction = 0.855,
                source = "STANDARD_GASOLINE"
            )
        }
    }
}
