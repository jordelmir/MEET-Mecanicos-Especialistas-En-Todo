package com.elysium369.meet.ride.reputation

import org.junit.Assert.*
import org.junit.Test

class DriverTrustEngineTest {

    @Test
    fun new_driver_first_5_star_rating_is_smoothed_by_prior() {
        // A single 5 star review does not immediately give 5.00
        val bayesian = DriverTrustEngine.calculateBayesianRating(
            currentRatingCount = 0,
            currentBayesianRating = null,
            newRating = 5,
        )
        // Expected: (10 * 4.80 + 5) / (10 + 0 + 1) = 53 / 11 ≈ 4.82
        assertEquals(4.82, bayesian, 0.01)
    }

    @Test
    fun high_volume_driver_converges_to_empirical_ratings() {
        // Driver with 500 reviews of 5 stars
        val bayesian = DriverTrustEngine.calculateBayesianRating(
            currentRatingCount = 500,
            currentBayesianRating = 4.98,
            newRating = 5,
        )
        // With high volume, prior has minimal effect
        assertTrue(bayesian >= 4.97)
    }

    @Test
    fun confidence_score_increases_with_sample_size() {
        val conf0 = DriverTrustEngine.calculateConfidenceScore(0)
        val conf10 = DriverTrustEngine.calculateConfidenceScore(10)
        val conf50 = DriverTrustEngine.calculateConfidenceScore(50)
        val conf200 = DriverTrustEngine.calculateConfidenceScore(200)

        assertEquals(0.0, conf0, 0.001)
        assertTrue(conf10 > conf0)
        assertTrue(conf50 > conf10)
        assertTrue(conf200 > conf50)
        assertTrue(conf200 >= 0.98) // High statistical confidence
    }

    @Test
    fun resolve_tier_assigns_vanguard_only_with_evidence() {
        // High trips + high rating -> VANGUARD
        val tierVanguard = DriverTrustEngine.resolveTier(500, 4.95)
        assertEquals(DriverTrustTier.VANGUARD, tierVanguard)

        // High trips but lower rating -> TRUSTED/ELITE, not VANGUARD
        val tierNotVanguard = DriverTrustEngine.resolveTier(500, 4.70)
        assertNotEquals(DriverTrustTier.VANGUARD, tierNotVanguard)

        // Perfect rating but only 5 trips -> VERIFIED (insufficient volume)
        val tierLowVolume = DriverTrustEngine.resolveTier(5, 5.0)
        assertEquals(DriverTrustTier.VERIFIED, tierLowVolume)
    }
}
