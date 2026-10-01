package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePassengerPreferencesMotorcycleTest {
    @Test fun motorcycleRoundTripsAsOnePassengerRequest() {
        val selected = RidePassengerPreferences(vehicleKind = RideVehicleKind.MOTORCYCLE)
        assertEquals(RideVehicleKind.MOTORCYCLE, RidePassengerPreferences.fromJson(selected.toJson()).vehicleKind)
        assertTrue(selected.toBadges().any { it.label.contains("1 pasajero") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun motorcycleCannotRequestAdditionalPassengers() {
        RidePassengerPreferences(vehicleKind = RideVehicleKind.MOTORCYCLE, fivePassengers = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun motorcycleCannotRequestChildCompanion() {
        RidePassengerPreferences(vehicleKind = RideVehicleKind.MOTORCYCLE, kidsCount = 1)
    }
}
