package com.elysium369.meet.ride.automatch

import com.elysium369.meet.ride.presence.RideDriverAvailability
import com.elysium369.meet.ride.reputation.DriverTrustTier
import org.junit.Assert.*
import org.junit.Test

class RideAutoMatchEvaluatorTest {

    private val basePolicy = RideAutoMatchPolicy(
        requestId = "req-1",
        enabled = true,
        strategyRaw = "FASTEST_PICKUP",
        maxFareMinor = 3500_00L, // 3500 CRC
        minimumTrustTierRaw = "VERIFIED",
        maximumEtaSeconds = 600, // 10 min
        allowFinishingPreviousTrip = false,
    )

    private val driverA = AutoMatchOfferCandidate(
        offerId = "off-A",
        driverId = "driver-A",
        vehicleId = "veh-A",
        offeredFareMinor = 3200_00L,
        etaMinutes = 3,
        trustTier = DriverTrustTier.TRUSTED,
        bayesianRating = 4.85,
        totalTrips = 80,
        driverAvailability = RideDriverAvailability.AVAILABLE,
    )

    private val driverB = AutoMatchOfferCandidate(
        offerId = "off-B",
        driverId = "driver-B",
        vehicleId = "veh-B",
        offeredFareMinor = 2900_00L,
        etaMinutes = 7,
        trustTier = DriverTrustTier.VERIFIED,
        bayesianRating = 4.60,
        totalTrips = 15,
        driverAvailability = RideDriverAvailability.AVAILABLE,
    )

    private val driverVanguard = AutoMatchOfferCandidate(
        offerId = "off-V",
        driverId = "driver-V",
        vehicleId = "veh-V",
        offeredFareMinor = 3300_00L,
        etaMinutes = 4,
        trustTier = DriverTrustTier.VANGUARD,
        bayesianRating = 4.98,
        totalTrips = 2952,
        driverAvailability = RideDriverAvailability.AVAILABLE,
    )

    @Test
    fun fastest_pickup_selects_lowest_eta() {
        val policy = basePolicy.copy(strategyRaw = "FASTEST_PICKUP")
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverB, driverVanguard))
        assertNotNull(match)
        assertEquals("off-A", match?.offerId) // 3 min ETA
    }

    @Test
    fun lowest_fare_selects_cheapest_eligible_offer() {
        val policy = basePolicy.copy(strategyRaw = "LOWEST_FARE")
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverB, driverVanguard))
        assertNotNull(match)
        assertEquals("off-B", match?.offerId) // 2900 CRC
    }

    @Test
    fun highest_trust_selects_vanguard_driver() {
        val policy = basePolicy.copy(strategyRaw = "HIGHEST_TRUST")
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverB, driverVanguard))
        assertNotNull(match)
        assertEquals("off-V", match?.offerId) // Vanguard 4.98 / 2952 trips
    }

    @Test
    fun rejects_candidate_above_max_fare() {
        val policy = basePolicy.copy(maxFareMinor = 3000_00L) // Limit to 3000 CRC
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverVanguard))
        assertNull(match) // Both driverA (3200) and driverV (3300) are above 3000
    }

    @Test
    fun rejects_candidate_exceeding_max_eta() {
        val policy = basePolicy.copy(maximumEtaSeconds = 300) // 5 min max
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverB)) // driverB has 7 min ETA
        assertNull(match)
    }

    @Test
    fun minimum_trust_tier_filtering_excludes_lower_tiers() {
        val policy = basePolicy.copy(minimumTrustTierRaw = "ELITE") // Requires ELITE or VANGUARD
        val match = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverB)) // A is TRUSTED, B is VERIFIED
        assertNull(match)

        val matchWithVanguard = RideAutoMatchEvaluator.findBestMatch(policy, listOf(driverA, driverVanguard))
        assertNotNull(matchWithVanguard)
        assertEquals("off-V", matchWithVanguard?.offerId)
    }

    @Test
    fun rejects_finishing_trip_driver_when_policy_disallows() {
        val busyDriver = driverA.copy(driverAvailability = RideDriverAvailability.FINISHING_CURRENT_TRIP)
        val policyDisallow = basePolicy.copy(allowFinishingPreviousTrip = false)
        assertNull(RideAutoMatchEvaluator.findBestMatch(policyDisallow, listOf(busyDriver)))

        val policyAllow = basePolicy.copy(allowFinishingPreviousTrip = true)
        assertNotNull(RideAutoMatchEvaluator.findBestMatch(policyAllow, listOf(busyDriver)))
    }
}
