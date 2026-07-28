package com.elysium369.meet.ride.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCancellationTest {

    @Test
    fun `safety cancellation reasons require review without an automatic fee`() {
        val safetyReasons = listOf(
            RideCancellationReason.SAFETY_CONCERN,
            RideCancellationReason.UNACCOMPANIED_MINOR,
            RideCancellationReason.CHILD_SEAT_REQUIRED,
            RideCancellationReason.IDENTITY_MISMATCH,
            RideCancellationReason.VEHICLE_MISMATCH,
            RideCancellationReason.HARASSMENT,
            RideCancellationReason.DANGEROUS_PICKUP,
            RideCancellationReason.MEDICAL_EMERGENCY,
            RideCancellationReason.UNSAFE_VEHICLE_CONDITION,
        )

        safetyReasons.forEach { reason ->
            val decision = RideCancellationPolicy.evaluate(reason)
            assertTrue("$reason must be reviewed", decision.requiresSafetyReview)
            assertFalse("$reason cannot create an automatic fee", decision.automaticFeeAllowed)
        }
    }

    @Test
    fun `pilot never assigns cancellation fees without human policy review`() {
        RideCancellationReason.entries.forEach { reason ->
            assertFalse(RideCancellationPolicy.evaluate(reason).automaticFeeAllowed)
        }
    }

    @Test
    fun `other reason requires a bounded explanation`() {
        assertFalse(
            RideCancellationPolicy.isDetailValid(
                RideCancellationReason.OTHER,
                detail = null,
            ),
        )
        assertTrue(
            RideCancellationPolicy.isDetailValid(
                RideCancellationReason.OTHER,
                detail = "El punto cambió por cierre de carretera",
            ),
        )
        assertFalse(
            RideCancellationPolicy.isDetailValid(
                RideCancellationReason.OTHER,
                detail = "x".repeat(501),
            ),
        )
    }

    @Test
    fun `passenger and driver only receive cancellation reasons for their role`() {
        val passenger = RideCancellationPolicy.reasonsFor(RideActorRole.PASSENGER)
        val driver = RideCancellationPolicy.reasonsFor(RideActorRole.DRIVER)

        assertTrue(RideCancellationReason.DRIVER_NO_SHOW in passenger)
        assertFalse(RideCancellationReason.PASSENGER_NO_SHOW in passenger)
        assertTrue(RideCancellationReason.PASSENGER_NO_SHOW in driver)
        assertFalse(RideCancellationReason.DRIVER_NO_SHOW in driver)
        assertFalse(RideCancellationReason.UNACCOMPANIED_MINOR in passenger)
        assertTrue(RideCancellationReason.UNACCOMPANIED_MINOR in driver)
    }
}
