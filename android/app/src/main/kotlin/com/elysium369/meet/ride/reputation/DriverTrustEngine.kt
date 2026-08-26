package com.elysium369.meet.ride.reputation

import kotlin.math.exp
import kotlin.math.round

/**
 * Pure domain Bayesian reputation calculator.
 * Prevents 1-trip 5.0 rating inflation while maintaining strict empirical statistical truth.
 */
object DriverTrustEngine {
    const val GLOBAL_PRIOR_WEIGHT = 10.0
    const val GLOBAL_PRIOR_MEAN = 4.80

    /**
     * Calculates the smoothed Bayesian rating:
     * R_bayesian = (C * m + sum(ratings)) / (C + n)
     */
    fun calculateBayesianRating(
        currentRatingCount: Int,
        currentBayesianRating: Double?,
        newRating: Int,
        priorWeight: Double = GLOBAL_PRIOR_WEIGHT,
        priorMean: Double = GLOBAL_PRIOR_MEAN,
    ): Double {
        val effectiveCurrentRating = currentBayesianRating ?: priorMean
        val numerator = (priorWeight * priorMean) + (currentRatingCount * effectiveCurrentRating) + newRating
        val denominator = priorWeight + currentRatingCount + 1
        val result = numerator / denominator
        return round(result * 100.0) / 100.0
    }

    /**
     * Statistical confidence score from 0.000 to 1.000 based on sample size:
     * confidence = 1 - exp(-n / 50)
     */
    fun calculateConfidenceScore(ratingCount: Int): Double {
        val confidence = 1.0 - exp(-ratingCount.toDouble() / 50.0)
        return round(confidence * 1000.0) / 1000.0
    }

    /**
     * Resolves the earned DriverTrustTier based on completed trips and Bayesian rating.
     */
    fun resolveTier(
        completedTrips: Int,
        bayesianRating: Double?,
    ): DriverTrustTier {
        val rating = bayesianRating ?: 0.0
        return when {
            completedTrips >= DriverTrustTier.VANGUARD.minCompletedTrips && rating >= DriverTrustTier.VANGUARD.minBayesianRating -> DriverTrustTier.VANGUARD
            completedTrips >= DriverTrustTier.ELITE.minCompletedTrips && rating >= DriverTrustTier.ELITE.minBayesianRating -> DriverTrustTier.ELITE
            completedTrips >= DriverTrustTier.TRUSTED.minCompletedTrips && rating >= DriverTrustTier.TRUSTED.minBayesianRating -> DriverTrustTier.TRUSTED
            else -> DriverTrustTier.VERIFIED
        }
    }
}
