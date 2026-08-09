package com.elysium369.meet.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRegistrationGatewayTest {

    @Test
    fun `passenger registration remains visible when driver account already exists`() {
        assertTrue(
            requiresRideRoleRegistration(
                driverMode = false,
                passengerRegistrationExists = false,
                driverRegistrationExists = true,
            ),
        )
    }

    @Test
    fun `passenger dashboard opens when passenger account exists`() {
        assertFalse(
            requiresRideRoleRegistration(
                driverMode = false,
                passengerRegistrationExists = true,
                driverRegistrationExists = false,
            ),
        )
    }

    @Test
    fun `driver registration remains visible when passenger account already exists`() {
        assertTrue(
            requiresRideRoleRegistration(
                driverMode = true,
                passengerRegistrationExists = true,
                driverRegistrationExists = false,
            ),
        )
    }

    @Test
    fun `driver dashboard opens when driver account exists`() {
        assertFalse(
            requiresRideRoleRegistration(
                driverMode = true,
                passengerRegistrationExists = false,
                driverRegistrationExists = true,
            ),
        )
    }
}
