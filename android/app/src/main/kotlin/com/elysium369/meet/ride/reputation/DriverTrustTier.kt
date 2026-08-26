package com.elysium369.meet.ride.reputation

/**
 * Verifiable trust tiers in Elysium Vanguard Mobility.
 * Tiers are earned exclusively through verified trips, Bayesian rating, and background verification.
 */
enum class DriverTrustTier(
    val id: String,
    val displayName: String,
    val minCompletedTrips: Int,
    val minBayesianRating: Double,
    val badgeLabel: String,
) {
    VERIFIED(
        id = "VERIFIED",
        displayName = "Verificado",
        minCompletedTrips = 0,
        minBayesianRating = 0.0,
        badgeLabel = "Conductor Verificado",
    ),
    TRUSTED(
        id = "TRUSTED",
        displayName = "Confiable",
        minCompletedTrips = 20,
        minBayesianRating = 4.50,
        badgeLabel = "Conductor de Confianza",
    ),
    ELITE(
        id = "ELITE",
        displayName = "Elite",
        minCompletedTrips = 100,
        minBayesianRating = 4.80,
        badgeLabel = "Conductor Elite",
    ),
    VANGUARD(
        id = "VANGUARD",
        displayName = "Vanguard",
        minCompletedTrips = 500,
        minBayesianRating = 4.90,
        badgeLabel = "Vanguard Driver",
    );

    companion object {
        fun fromId(id: String?): DriverTrustTier {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: VERIFIED
        }
    }
}
