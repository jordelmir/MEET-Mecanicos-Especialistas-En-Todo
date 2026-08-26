package com.elysium369.meet.ride.automatch

import com.elysium369.meet.ride.presence.RideDriverAvailability
import com.elysium369.meet.ride.reputation.DriverTrustTier

data class AutoMatchOfferCandidate(
    val offerId: String,
    val driverId: String,
    val vehicleId: String,
    val offeredFareMinor: Long,
    val etaMinutes: Int,
    val trustTier: DriverTrustTier,
    val bayesianRating: Double?,
    val totalTrips: Int,
    val driverAvailability: RideDriverAvailability,
)

/**
 * Pure domain candidate evaluator for Auto-Match.
 * Evaluates candidate offers deterministically based on policy constraints and ranking strategies.
 */
object RideAutoMatchEvaluator {

    fun findBestMatch(
        policy: RideAutoMatchPolicy,
        candidates: List<AutoMatchOfferCandidate>,
    ): AutoMatchOfferCandidate? {
        if (!policy.enabled || candidates.isEmpty()) return null

        val minTierRank = policy.minimumTrustTier.ordinal

        // Filter eligible candidates
        val eligible = candidates.filter { candidate ->
            val fareOk = candidate.offeredFareMinor <= policy.maxFareMinor
            val etaOk = (candidate.etaMinutes * 60) <= policy.maximumEtaSeconds
            val tierOk = candidate.trustTier.ordinal >= minTierRank
            val availabilityOk = candidate.driverAvailability == RideDriverAvailability.AVAILABLE ||
                    (policy.allowFinishingPreviousTrip && candidate.driverAvailability == RideDriverAvailability.FINISHING_CURRENT_TRIP)

            fareOk && etaOk && tierOk && availabilityOk
        }

        if (eligible.isEmpty()) return null

        // Rank by policy strategy
        return when (policy.strategy) {
            RideAutoMatchStrategy.FASTEST_PICKUP -> {
                eligible.minWithOrNull(
                    compareBy<AutoMatchOfferCandidate> { it.etaMinutes }
                        .thenBy { it.offeredFareMinor }
                )
            }
            RideAutoMatchStrategy.LOWEST_FARE -> {
                eligible.minWithOrNull(
                    compareBy<AutoMatchOfferCandidate> { it.offeredFareMinor }
                        .thenBy { it.etaMinutes }
                )
            }
            RideAutoMatchStrategy.HIGHEST_TRUST -> {
                eligible.maxWithOrNull(
                    compareBy<AutoMatchOfferCandidate> { it.trustTier.ordinal }
                        .thenBy { it.bayesianRating ?: 0.0 }
                        .thenByDescending { it.etaMinutes }
                )
            }
            RideAutoMatchStrategy.BALANCED -> {
                eligible.maxByOrNull { calculateBalancedScore(it) }
            }
        }
    }

    private fun calculateBalancedScore(candidate: AutoMatchOfferCandidate): Double {
        val etaScore = (10.0 - candidate.etaMinutes.coerceAtMost(10)).coerceAtLeast(0.0) * 0.4
        val tierScore = (candidate.trustTier.ordinal + 1) * 0.3
        val ratingScore = (candidate.bayesianRating ?: 4.0) * 0.3
        return etaScore + tierScore + ratingScore
    }
}
