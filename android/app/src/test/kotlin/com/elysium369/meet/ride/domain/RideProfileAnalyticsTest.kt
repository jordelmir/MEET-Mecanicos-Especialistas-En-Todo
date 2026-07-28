package com.elysium369.meet.ride.domain

import com.elysium369.meet.data.local.entities.RideRequestEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideProfileAnalyticsTest {
    private val zone = ZoneId.of("America/Costa_Rica")
    private val now = ZonedDateTime.of(2026, 7, 28, 10, 0, 0, 0, zone)

    @Test
    fun `passenger annual spend and driver earnings use completed real trips only`() {
        val rides = listOf(
            ride("done", "COMPLETED", 3_000.0, now.minusDays(1), 5.0),
            ride("open", "OPEN", 9_000.0, now, null),
            ride("old", "COMPLETED", 2_000.0, now.minusYears(1), 4.0),
        )

        val passenger = RideProfileAnalytics.passenger(
            rides, "p1", now.toInstant().toEpochMilli(), zone,
        )
        val driver = RideProfileAnalytics.driver(
            rides, "d1", now.toInstant().toEpochMilli(), zone,
        )

        assertEquals(3_000.0, passenger.money.year, 0.0)
        assertEquals(3_000.0, driver.money.week, 0.0)
        assertEquals(5_000.0, driver.money.rollingThreeYears, 0.0)
        assertEquals(2, driver.capturedRatings)
        assertEquals(4.5, driver.averageRating ?: 0.0, 0.0)
    }

    @Test
    fun `rating stays absent when no completed ride captured one`() {
        val summary = RideProfileAnalytics.passenger(
            listOf(ride("open", "OPEN", 3_000.0, now, null)),
            "p1",
            now.toInstant().toEpochMilli(),
            zone,
        )
        assertNull(summary.averageRating)
        assertEquals(0, summary.completedTrips)
    }

    private fun ride(
        id: String,
        status: String,
        amount: Double,
        moment: ZonedDateTime,
        passengerRating: Double?,
    ) = RideRequestEntity(
        requestId = id,
        passengerId = "p1",
        passengerName = "Pasajero",
        passengerPhone = "",
        pickupLatitude = 0.0,
        pickupLongitude = 0.0,
        pickupAddress = "Origen",
        pickupAccuracy = 5f,
        destLatitude = 0.0,
        destLongitude = 0.0,
        destAddress = "Destino",
        priceOffer = amount,
        currency = "CRC",
        estimatedDistanceKm = 10.0,
        estimatedDurationMin = 20,
        status = status,
        assignedDriverId = "d1",
        finalPrice = amount,
        passengerRating = passengerRating,
        createdAt = moment.minusMinutes(20).toInstant().toEpochMilli(),
        completedAt = if (status == "COMPLETED") moment.toInstant().toEpochMilli() else null,
    )
}
