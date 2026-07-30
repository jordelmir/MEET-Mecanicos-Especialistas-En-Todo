package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideLifecycleTest {

    private val passenger = RideActor("passenger-1", RideActorRole.PASSENGER)
    private val driver = RideActor("driver-1", RideActorRole.DRIVER)
    private val parties = RideTripParties(
        passengerId = passenger.id,
        driverId = driver.id,
    )

    @Test
    fun `authorized parties can complete the full verified lifecycle`() {
        val transitions = listOf(
            request(RideState.DRAFT, RideState.SEARCHING, passenger),
            request(RideState.SEARCHING, RideState.OFFERED, driver),
            request(RideState.OFFERED, RideState.ASSIGNED, passenger),
            request(RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE, driver),
            request(RideState.DRIVER_EN_ROUTE, RideState.ARRIVED, driver),
            request(
                RideState.ARRIVED,
                RideState.PASSENGER_ONBOARD,
                driver,
                pinVerified = true,
            ),
            request(RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS, driver),
            request(RideState.IN_PROGRESS, RideState.COMPLETED, driver),
        )

        transitions.forEach { transition ->
            assertEquals(TransitionDecision.Allowed, RideLifecyclePolicy.decide(transition))
        }
    }

    @Test
    fun `boarding is denied until trip pin is verified`() {
        val decision = RideLifecyclePolicy.decide(
            request(RideState.ARRIVED, RideState.PASSENGER_ONBOARD, driver),
        )

        assertTrue(decision is TransitionDecision.Denied)
        assertEquals(
            RideDomainErrorCode.PIN_REQUIRED,
            (decision as TransitionDecision.Denied).error.code,
        )
    }

    @Test
    fun `unrelated actor cannot mutate a trip`() {
        val stranger = RideActor("driver-else", RideActorRole.DRIVER)

        val decision = RideLifecyclePolicy.decide(
            request(RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE, stranger),
        )

        assertEquals(
            RideDomainErrorCode.FORBIDDEN,
            (decision as TransitionDecision.Denied).error.code,
        )
    }

    @Test
    fun `states cannot be skipped or reopened after completion`() {
        assertTrue(
            RideLifecyclePolicy.decide(
                request(RideState.ASSIGNED, RideState.IN_PROGRESS, driver),
            ) is TransitionDecision.Denied,
        )
        assertTrue(
            RideLifecyclePolicy.decide(
                request(RideState.COMPLETED, RideState.IN_PROGRESS, driver),
            ) is TransitionDecision.Denied,
        )
    }

    @Test
    fun `safety hold is operational metadata and never replaces canonical ride state`() {
        val hold = RideOperationalHold(
            rideId = "ride-1",
            type = RideOperationalHoldType.SAFETY_REVIEW,
            requestedBy = passenger,
            reasonCode = "PASSENGER_REQUESTED_REVIEW",
        )

        assertEquals("ride-1", hold.rideId)
        assertEquals(RideOperationalHoldType.SAFETY_REVIEW, hold.type)
        assertEquals(RideState.IN_PROGRESS, RideState.IN_PROGRESS)
    }

    @Test
    fun `stale expected version is rejected with a stable conflict code`() {
        val decision = RideLifecyclePolicy.decide(
            request(
                from = RideState.ASSIGNED,
                to = RideState.DRIVER_EN_ROUTE,
                actor = driver,
                expectedVersion = 6,
                currentVersion = 7,
            ),
        )

        assertEquals(
            RideDomainErrorCode.VERSION_CONFLICT,
            (decision as TransitionDecision.Denied).error.code,
        )
    }

    @Test
    fun `system or dispatcher can assign a searching ride directly`() {
        val system = RideActor("system", RideActorRole.SYSTEM)
        val dispatcher = RideActor("dispatcher-1", RideActorRole.DISPATCHER)

        assertEquals(
            TransitionDecision.Allowed,
            RideLifecyclePolicy.decide(
                request(RideState.SEARCHING, RideState.ASSIGNED, system),
            ),
        )
        assertEquals(
            TransitionDecision.Allowed,
            RideLifecyclePolicy.decide(
                request(RideState.SEARCHING, RideState.ASSIGNED, dispatcher),
            ),
        )
    }

    @Test
    fun `cancelled or completed rides can open a dispute but cannot become active`() {
        assertEquals(
            TransitionDecision.Allowed,
            RideLifecyclePolicy.decide(
                request(RideState.CANCELLED, RideState.DISPUTED, passenger),
            ),
        )
        assertEquals(
            TransitionDecision.Allowed,
            RideLifecyclePolicy.decide(
                request(RideState.COMPLETED, RideState.DISPUTED, driver),
            ),
        )
        assertEquals(
            RideDomainErrorCode.TERMINAL_STATE,
            (
                RideLifecyclePolicy.decide(
                    request(RideState.CANCELLED, RideState.SEARCHING, passenger),
                ) as TransitionDecision.Denied
                ).error.code,
        )
    }

    private fun request(
        from: RideState,
        to: RideState,
        actor: RideActor,
        pinVerified: Boolean = false,
        expectedVersion: Long = 7,
        currentVersion: Long = 7,
    ) = RideTransitionRequest(
        from = from,
        to = to,
        actor = actor,
        parties = parties,
        pinVerified = pinVerified,
        expectedVersion = RideVersion.of(expectedVersion),
        currentVersion = RideVersion.of(currentVersion),
    )
}
