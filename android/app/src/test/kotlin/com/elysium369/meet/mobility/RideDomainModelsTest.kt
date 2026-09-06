package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.DispatchMode
import com.elysium369.meet.mobility.domain.models.DriverOfferState
import com.elysium369.meet.mobility.domain.models.DriverRideOffer
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.RideEta
import com.elysium369.meet.mobility.domain.models.RideRequest
import com.elysium369.meet.mobility.domain.models.RideRequestState
import com.elysium369.meet.mobility.domain.models.RideStop
import com.elysium369.meet.mobility.domain.models.RideStopType
import com.elysium369.meet.mobility.domain.models.ServiceCategoryId
import com.elysium369.meet.mobility.domain.models.Trip
import com.elysium369.meet.mobility.domain.models.TripState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RideDomainModelsTest {

    @Test
    fun rideStopValidatesCoordinatesAndAccuracy() {
        val validStop = RideStop(
            stopId = UUID.randomUUID(),
            sequence = 0,
            latitude = 9.9355,
            longitude = -84.0768,
            accuracyMeters = 4.5f,
            displayName = "Teatro Nacional",
            address = "Avenida Segunda, San Jose",
            placeId = null,
            type = RideStopType.PICKUP,
        )
        assertNotNull(validStop)

        // Out-of-range latitude
        assertThrows(IllegalArgumentException::class.java) {
            validStop.copy(latitude = 95.0)
        }

        // Out-of-range longitude
        assertThrows(IllegalArgumentException::class.java) {
            validStop.copy(longitude = -190.0)
        }

        // Negative accuracy
        assertThrows(IllegalArgumentException::class.java) {
            validStop.copy(accuracyMeters = -1.0f)
        }

        // Negative sequence
        assertThrows(IllegalArgumentException::class.java) {
            validStop.copy(sequence = -1)
        }
    }

    @Test
    fun rideRequestInvariants() {
        val pickup = RideStop(
            stopId = UUID.randomUUID(),
            sequence = 0,
            latitude = 9.935,
            longitude = -84.075,
            accuracyMeters = null,
            displayName = null,
            address = null,
            placeId = null,
            type = RideStopType.PICKUP,
        )
        val dest = RideStop(
            stopId = UUID.randomUUID(),
            sequence = 1,
            latitude = 9.928,
            longitude = -84.090,
            accuracyMeters = null,
            displayName = null,
            address = null,
            placeId = null,
            type = RideStopType.DESTINATION,
        )

        val req = RideRequest(
            rideRequestId = UUID.randomUUID(),
            riderId = UUID.randomUUID(),
            marketId = MarketId("CR_SJO"),
            serviceCategoryId = ServiceCategoryId("cat_standard"),
            dispatchMode = DispatchMode.AUTO_DISPATCH,
            pickup = pickup,
            intermediateStops = emptyList(),
            destination = dest,
            requestedPrice = Money(3000L, CurrencyCode.of("CRC")),
            state = RideRequestState.SEARCHING,
            scheduledFor = null,
            serverVersion = 1L,
            correlationId = UUID.randomUUID(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        assertEquals(RideRequestState.SEARCHING, req.state)

        // Version < 1 throws
        assertThrows(IllegalArgumentException::class.java) {
            req.copy(serverVersion = 0L)
        }
    }

    @Test
    fun driverRideOfferInvariants() {
        val offer = DriverRideOffer(
            offerId = UUID.randomUUID(),
            rideRequestId = UUID.randomUUID(),
            driverId = UUID.randomUUID(),
            vehicleId = UUID.randomUUID(),
            offeredPrice = Money(3200L, CurrencyCode.of("CRC")),
            pickupEta = RideEta.Routing(durationSeconds = 180, distanceMeters = 1200),
            state = DriverOfferState.OPEN,
            expiresAt = Instant.now().plusSeconds(180),
            serverVersion = 1L,
            createdAt = Instant.now(),
        )
        assertEquals(DriverOfferState.OPEN, offer.state)

        assertThrows(IllegalArgumentException::class.java) {
            offer.copy(serverVersion = 0L)
        }
    }

    @Test
    fun tripInvariants() {
        val trip = Trip(
            tripId = UUID.randomUUID(),
            rideRequestId = UUID.randomUUID(),
            riderId = UUID.randomUUID(),
            driverId = UUID.randomUUID(),
            vehicleId = UUID.randomUUID(),
            state = TripState.ASSIGNED,
            verificationPinHash = null,
            quoteId = null,
            paymentAuthorizationId = null,
            settlementId = null,
            serverVersion = 1L,
            assignedAt = Instant.now(),
            startedAt = null,
            completedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        assertEquals(TripState.ASSIGNED, trip.state)

        assertThrows(IllegalArgumentException::class.java) {
            trip.copy(serverVersion = 0L)
        }
    }
}
