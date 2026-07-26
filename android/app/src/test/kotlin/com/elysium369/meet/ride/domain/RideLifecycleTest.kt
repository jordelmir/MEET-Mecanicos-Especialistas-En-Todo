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
                passenger,
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
            request(RideState.ARRIVED, RideState.PASSENGER_ONBOARD, passenger),
        )

        assertTrue(decision is TransitionDecision.Denied)
        assertEquals("PIN de viaje requerido", (decision as TransitionDecision.Denied).reason)
    }

    @Test
    fun `unrelated actor cannot mutate a trip`() {
        val stranger = RideActor("driver-else", RideActorRole.DRIVER)

        val decision = RideLifecyclePolicy.decide(
            request(RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE, stranger),
        )

        assertEquals(
            TransitionDecision.Denied("Actor no autorizado para este viaje"),
            decision,
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
    fun `a participant may request a safety hold during an active trip`() {
        val decision = RideLifecyclePolicy.decide(
            request(RideState.IN_PROGRESS, RideState.SAFETY_HOLD, passenger),
        )

        assertEquals(TransitionDecision.Allowed, decision)
    }

    private fun request(
        from: RideState,
        to: RideState,
        actor: RideActor,
        pinVerified: Boolean = false,
    ) = RideTransitionRequest(
        from = from,
        to = to,
        actor = actor,
        parties = parties,
        pinVerified = pinVerified,
    )
}
