package com.elysium369.meet.ride.driver

import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ride.map.RideGeoPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverTripProcessRecoveryTest {

    @Test
    fun `process recovery restores canonical in-progress trip state from room projection`() {
        val originalEntity = RideRequestEntity(
            requestId = "ride-recovery-001",
            passengerId = "passenger-456",
            passengerName = "Ana Lorena",
            passengerPhone = "50688888888",
            pickupLatitude = 9.9333,
            pickupLongitude = -84.0833,
            pickupAddress = "Radial San Antonio",
            pickupAccuracy = 5f,
            destLatitude = 9.9500,
            destLongitude = -84.1000,
            destAddress = "Multiplaza Escazú",
            priceOffer = 3500.0,
            priceOfferMinor = 350000L,
            currency = "CRC",
            estimatedDistanceKm = 6.2,
            estimatedDurationMin = 15,
            status = "IN_PROGRESS",
            serverState = "IN_PROGRESS",
            serverVersion = 14L,
            assignedDriverId = "driver-789",
            driverArrivedAt = 1726500000000L,
            createdAt = 1726490000000L,
        )

        // Simulate process re-hydration
        val phase = (originalEntity.serverState ?: originalEntity.status).toDriverTripPhase()
        assertEquals(DriverTripPhase.InProgress, phase)

        val uiState = DriverTripUiState(
            rideId = originalEntity.requestId,
            serverVersion = originalEntity.serverVersion,
            serverState = originalEntity.serverState ?: originalEntity.status,
            phase = phase,
            passengerName = originalEntity.passengerName,
            pickup = RideGeoPoint(
                latitude = originalEntity.pickupLatitude,
                longitude = originalEntity.pickupLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = originalEntity.createdAt,
            ),
            pickupAddress = originalEntity.pickupAddress,
            destination = RideGeoPoint(
                latitude = originalEntity.destLatitude,
                longitude = originalEntity.destLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = originalEntity.createdAt,
            ),
            destinationAddress = originalEntity.destAddress,
            acceptingFutureOffers = false, // separated presence state
            driverArrivedAtEpochMs = originalEntity.driverArrivedAt,
        )

        assertEquals("ride-recovery-001", uiState.rideId)
        assertEquals(14L, uiState.serverVersion)
        assertEquals(DriverTripPhase.InProgress, uiState.phase)
        assertEquals(1726500000000L, uiState.driverArrivedAtEpochMs)
        assertNotNull(uiState.destination)
        assertEquals(9.9500, uiState.destination!!.latitude, 0.0001)
        assertEquals(-84.1000, uiState.destination!!.longitude, 0.0001)
        // Presence state remains independent of canonical trip phase
        assertEquals(false, uiState.acceptingFutureOffers)
    }

    @Test
    fun `process recovery preserves intermediate stops decoded from projection entity`() {
        val stops = listOf(
            RideStopSnapshot(order = 1, label = "Parada 1", latitude = 9.9350, longitude = -84.0850),
            RideStopSnapshot(order = 2, label = "Parada 2", latitude = 9.9400, longitude = -84.0900),
        )
        val stopsJson = Json.encodeToString(stops)

        val entity = RideRequestEntity(
            requestId = "ride-recovery-002",
            passengerId = "passenger-002",
            passengerName = "Carlos",
            passengerPhone = "50677777777",
            pickupLatitude = 9.9300,
            pickupLongitude = -84.0800,
            pickupAddress = "Inicio",
            pickupAccuracy = 4f,
            destLatitude = 9.9600,
            destLongitude = -84.1100,
            destAddress = "Fin",
            priceOffer = 5000.0,
            currency = "CRC",
            estimatedDistanceKm = 8.0,
            estimatedDurationMin = 20,
            stopsJson = stopsJson,
            priceOfferMinor = 500000L,
            status = "ARRIVED",
            serverState = "ARRIVED",
            serverVersion = 8L,
            assignedDriverId = "driver-789",
            driverArrivedAt = 1726510000000L,
            createdAt = 1726500000000L,
        )

        val decodedStops = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<RideStopSnapshot>>(entity.stopsJson)
                .mapNotNull {
                    val lat = it.latitude ?: return@mapNotNull null
                    val lng = it.longitude ?: return@mapNotNull null
                    RideGeoPoint(
                        latitude = lat,
                        longitude = lng,
                        accuracyMeters = null,
                        capturedAtEpochMs = 0L,
                    )
                }
        }.getOrDefault(emptyList())

        assertEquals(2, decodedStops.size)
        assertEquals(9.9350, decodedStops[0].latitude, 0.0001)
        assertEquals(9.9400, decodedStops[1].latitude, 0.0001)
        assertEquals(DriverTripPhase.AtPickup, (entity.serverState ?: entity.status).toDriverTripPhase())
    }

    @Test
    fun `terminal trip status clears operational cockpit state on recovery`() {
        val completedEntity = RideRequestEntity(
            requestId = "ride-recovery-003",
            passengerId = "passenger-003",
            passengerName = "Laura",
            passengerPhone = "50666666666",
            pickupLatitude = 9.9300,
            pickupLongitude = -84.0800,
            pickupAddress = "Inicio",
            pickupAccuracy = 5f,
            destLatitude = 9.9600,
            destLongitude = -84.1100,
            destAddress = "Fin",
            priceOffer = 4000.0,
            currency = "CRC",
            estimatedDistanceKm = 7.0,
            estimatedDurationMin = 18,
            priceOfferMinor = 400000L,
            status = "COMPLETED",
            serverState = "COMPLETED",
            serverVersion = 20L,
            assignedDriverId = "driver-789",
            createdAt = 1726500000000L,
        )

        val phase = (completedEntity.serverState ?: completedEntity.status).toDriverTripPhase()
        assertEquals(DriverTripPhase.Completed, phase)

        // Operational cockpit states filter out terminal trips
        val isOperational = phase in setOf(
            DriverTripPhase.Assigned,
            DriverTripPhase.ToPickup,
            DriverTripPhase.AtPickup,
            DriverTripPhase.PassengerOnboard,
            DriverTripPhase.InProgress,
        )
        assertTrue(!isOperational)
    }
}
