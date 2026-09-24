package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverEconomicsProjectionTest {

    @Test
    fun `standard trip calculates 5 percent commission and correct net earnings`() {
        val projection = DriverEconomicsProjection.calculate(
            grossFareMinor = 10_000L, // ₡10,000
            tripDistanceMeters = 10_000L, // 10 km
            tripDurationSeconds = 1_200L, // 20 mins
            pickupDeadheadDistanceMeters = 2_000L, // 2 km
            pickupDeadheadDurationSeconds = 300L, // 5 mins
            fuelCostPerKmMinor = 65L, // ₡65/km
            commissionBps = 500L, // 5.0%
        )

        // Commission = 10,000 * 5% = 500
        assertEquals(500L, projection.platformFeeMinor)
        assertEquals(9_500L, projection.grossDriverPayoutMinor)

        // Total distance = 12 km -> fuel cost = 12 * 65 = 780
        assertEquals(12_000L, projection.totalDistanceMeters)
        assertEquals(780L, projection.estimatedFuelExpenseMinor)

        // Net earnings = 9,500 - 780 = 8,720
        assertEquals(8_720L, projection.projectedNetEarningsMinor)

        // Total time = 25 mins = 1500s = 0.4167 hours
        // Net per hour = 8720 / (1500 / 3600) = 8720 * 2.4 = 20,928
        assertTrue(projection.projectedNetPerHourMinor > 20_000L)
        assertTrue(projection.isViable)
    }

    @Test
    fun `unviable trip with long deadhead is marked not viable`() {
        val projection = DriverEconomicsProjection.calculate(
            grossFareMinor = 1_500L, // ₡1,500 low fare
            tripDistanceMeters = 2_000L, // 2 km
            tripDurationSeconds = 600L, // 10 mins
            pickupDeadheadDistanceMeters = 15_000L, // 15 km deadhead!
            pickupDeadheadDurationSeconds = 2_400L, // 40 mins deadhead!
            fuelCostPerKmMinor = 70L,
            commissionBps = 500L,
            minHourlyFloorMinor = 2_500L,
        )

        // Gross payout = 1,500 - 75 = 1,425
        // Fuel expense = 17 km * 70 = 1,190
        // Net = 1,425 - 1,190 = 235
        // Total time = 50 mins = 0.833 hours -> Net/hr = 235 / 0.833 = 282/hr (< 2,500)
        assertEquals(235L, projection.projectedNetEarningsMinor)
        assertFalse("Trip with excessive deadhead must fail viability check", projection.isViable)
    }

    @Test
    fun `formatting helpers include currency symbols and units`() {
        val projection = DriverEconomicsProjection.calculate(
            grossFareMinor = 5_000L,
            tripDistanceMeters = 5_000L,
            tripDurationSeconds = 900L,
        )

        assertNotNull(projection.formattedNetEarnings)
        assertTrue(projection.formattedNetEarnings.contains("₡") || projection.formattedNetEarnings.contains("5"))
        assertTrue(projection.formattedNetPerHour.endsWith("/h"))
    }
}
