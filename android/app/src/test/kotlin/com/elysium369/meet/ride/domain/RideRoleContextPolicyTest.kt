package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideRoleContextPolicyTest {
    @Test
    fun oneAccountGetsIndependentPassengerAndDriverContextKeys() {
        val passenger = RideRoleContextPolicy.selectionOwnerKey("account-1", false)
        val driver = RideRoleContextPolicy.selectionOwnerKey("account-1", true)

        assertEquals("account-1#PASSENGER", passenger)
        assertEquals("account-1#RIDE_DRIVER", driver)
        assertNotEquals(passenger, driver)
    }

    @Test
    fun missingAccountNeverCreatesAContext() {
        assertNull(RideRoleContextPolicy.selectionOwnerKey(null, false))
        assertNull(RideRoleContextPolicy.selectionOwnerKey("", true))
    }
}
