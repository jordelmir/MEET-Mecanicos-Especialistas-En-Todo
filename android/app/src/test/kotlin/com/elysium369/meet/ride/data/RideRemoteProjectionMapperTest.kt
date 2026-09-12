package com.elysium369.meet.ride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideRemoteProjectionMapperTest {
    private fun remote(driver: String? = "driver-A", vehicle: String? = "vehicle-A", owner: String = "passenger-A", payment: String? = "CARD_TOKEN", state: String = "ASSIGNED") =
        RemoteRideRequestProjection(
            id = "ride-1", passengerId = owner, assignedDriverId = driver, assignedVehicleId = vehicle,
            pickupLatitude = 1.0, pickupLongitude = 2.0, pickupAddress = "Pickup",
            destinationLatitude = 3.0, destinationLongitude = 4.0, destinationAddress = "Destination",
            offeredFareMinor = 1500, currency = "USD", state = state, version = 2,
            paymentMethod = payment, createdAt = "2026-09-10T12:00:00Z",
        )

    private fun existing() = remote().toLocal(null, emptyList(), "offer-A").copy(
        passengerName = "Alice", passengerPhone = "private-A",
        assignedDriverName = "Driver A", assignedDriverPhone = "private-driver-A",
        assignedDriverVehicle = "Vehicle A", boardingPin = "1234", boardingPinExpiresAt = 5000,
        passengerRating = 5.0, tipAmountMinor = 200,
    )

    @Test fun `reassignment invalidates all dependent identity and pickup proof`() {
        val result = remote(driver = "driver-B", vehicle = "vehicle-B").toLocal(existing(), emptyList(), null)
        assertEquals("driver-B", result.assignedDriverId)
        assertNull(result.assignedDriverName)
        assertNull(result.assignedDriverPhone)
        assertNull(result.assignedDriverVehicle)
        assertNull(result.acceptedOfferId)
        assertNull(result.boardingPin)
        assertNull(result.boardingPinExpiresAt)
    }

    @Test fun `same driver with changed vehicle must not retain old vehicle or pin`() {
        val result = remote(vehicle = "vehicle-B").toLocal(existing(), emptyList(), null)
        assertNull(result.assignedDriverVehicle)
        assertNull(result.boardingPin)
        assertNull(result.acceptedOfferId)
    }

    @Test fun `authoritative participant projection replaces stale assignment identity`() {
        val result = remote(driver = "driver-B", vehicle = "vehicle-B").toLocal(
            existing = existing(),
            stops = emptyList(),
            acceptedOfferId = "offer-B",
            assignedDriverName = "Driver B",
            assignedDriverVehicle = "Vehicle B",
        )
        assertEquals("driver-B", result.assignedDriverId)
        assertEquals("Driver B", result.assignedDriverName)
        assertEquals("Vehicle B", result.assignedDriverVehicle)
        assertEquals("offer-B", result.acceptedOfferId)
        assertNull(result.assignedDriverPhone)
        assertNull(result.boardingPin)
    }

    @Test fun `unassignment and terminal state remove boarding credentials`() {
        val unassigned = remote(driver = null, vehicle = null, state = "SEARCHING").toLocal(existing(), emptyList(), null)
        assertNull(unassigned.assignedDriverName)
        assertNull(unassigned.boardingPin)
        assertNull(unassigned.acceptedOfferId)
        assertNull(remote(state = "CANCELLED").toLocal(existing(), emptyList(), null).boardingPin)
    }

    @Test fun `canonical passenger supersedes stale owner without inheriting private fields`() {
        val result = remote(owner = "passenger-B").toLocal(existing(), emptyList(), null)
        assertEquals("passenger-B", result.passengerId)
        assertEquals("Pasajero", result.passengerName)
        assertEquals("", result.passengerPhone)
        assertNull(result.assignedDriverPhone)
        assertNull(result.tipAmountMinor)
        assertNull(result.passengerRating)
    }

    @Test fun `missing or blank payment is unknown even when previous snapshot said cash`() {
        val previous = existing().copy(paymentMethod = "CASH")
        assertEquals("UNKNOWN", remote(payment = null).toLocal(previous, emptyList(), null).paymentMethod)
        assertEquals("UNKNOWN", remote(payment = " ").toLocal(null, emptyList(), null).paymentMethod)
        assertEquals("CARD_TOKEN", remote().toLocal(previous, emptyList(), null).paymentMethod)
    }

    @Test fun `unchanged assignment retains captured identity and preboarding proof`() {
        val result = remote().toLocal(existing(), emptyList(), null)
        assertEquals("Driver A", result.assignedDriverName)
        assertEquals("private-driver-A", result.assignedDriverPhone)
        assertEquals("Vehicle A", result.assignedDriverVehicle)
        assertEquals("1234", result.boardingPin)
        assertEquals("offer-A", result.acceptedOfferId)
        assertEquals(15.0, result.priceOffer, 0.0)
        assertEquals(1500L, result.priceOfferMinor)
    }
}
