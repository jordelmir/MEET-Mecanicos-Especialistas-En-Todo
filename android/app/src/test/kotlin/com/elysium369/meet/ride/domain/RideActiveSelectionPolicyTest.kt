package com.elysium369.meet.ride.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideActiveSelectionPolicyTest {
    @Test
    fun passengerCannotRestoreForeignRideSelectedInDriverMode() {
        assertFalse(
            RideActiveSelectionPolicy.canSelect(
                ownerId = "driver",
                driverMode = false,
                passengerId = "passenger",
                assignedDriverId = null,
                serverState = "SEARCHING",
                serverVersion = 1,
            ),
        )
    }

    @Test
    fun driverCanInspectPublishedOpenRideAndAssignedRide() {
        assertTrue(
            RideActiveSelectionPolicy.canSelect(
                "driver", true, "passenger", null, "SEARCHING", 1,
            ),
        )
        assertTrue(
            RideActiveSelectionPolicy.canSelect(
                "driver", true, "passenger", "driver", "DRIVER_EN_ROUTE", 8,
            ),
        )
    }

    @Test
    fun driverCannotInspectUnpublishedOrOtherDriversAssignedRide() {
        assertFalse(
            RideActiveSelectionPolicy.canSelect(
                "driver", true, "passenger", null, "PENDING_PUBLICATION", 0,
            ),
        )
        assertFalse(
            RideActiveSelectionPolicy.canSelect(
                "driver", true, "passenger", "other-driver", "ASSIGNED", 2,
            ),
        )
    }

    @Test
    fun driverCannotInspectOwnPassengerRequestAsOfferCandidate() {
        assertFalse(
            RideActiveSelectionPolicy.canSelect(
                "same-account", true, "same-account", null, "SEARCHING", 1,
            ),
        )
    }
}
