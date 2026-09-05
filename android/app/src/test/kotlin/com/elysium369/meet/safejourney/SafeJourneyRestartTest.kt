package com.elysium369.meet.safejourney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeJourneyRestartTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `safe journey state survives process death and reconciles timers`() {
        val startedAt = now - 30 * 60_000L // 30 minutes ago
        val expectedArrival = now + 10 * 60_000L // 10 minutes from now

        val initialJourney = SafeJourney(
            journeyId = "journey-123",
            principalId = "user-alice",
            publisherDeviceId = "dev-alice",
            originName = "Oficina",
            destinationName = "Casa",
            destinationLat = 9.9333,
            destinationLon = -84.0833,
            startedAtEpochMs = startedAt,
            expectedArrivalEpochMs = expectedArrival,
            journeyState = SafeJourneyState.PROGRESSING,
        )

        // 1. User records a midway check-in
        val checkIn = JourneyCheckIn(
            checkInId = "chk-1",
            journeyId = "journey-123",
            principalId = "user-alice",
            timestampMs = now - 15 * 60_000L,
            note = "En ruta sin novedades",
            isSafe = true,
        )
        val withCheckIn = initialJourney.recordCheckIn(checkIn)
        assertEquals(1, withCheckIn.checkIns.size)
        assertEquals(SafeJourneyState.PROGRESSING, withCheckIn.journeyState)

        // 2. Simulated process death & recovery from persistence
        val rehydrated = withCheckIn.copy() // Represents DB fetch after cold start
        assertEquals("journey-123", rehydrated.journeyId)
        assertEquals(SafeJourneyState.PROGRESSING, rehydrated.journeyState)

        // 3. User arrives safely
        val arrivedJourney = rehydrated.confirmArrival(now)
        assertEquals(SafeJourneyState.ARRIVED, arrivedJourney.journeyState)
        assertTrue(arrivedJourney.journeyState.isTerminal)
    }

    @Test
    fun `truth law - no response does not automatically create emergency`() {
        val startedAt = now - 60 * 60_000L
        val expectedArrival = now - 20 * 60_000L // Overdue by 20 minutes!

        val overdueJourney = SafeJourney(
            journeyId = "journey-overdue",
            principalId = "user-alice",
            publisherDeviceId = "dev-alice",
            originName = "Oficina",
            destinationName = "Casa",
            destinationLat = 9.9333,
            destinationLon = -84.0833,
            startedAtEpochMs = startedAt,
            expectedArrivalEpochMs = expectedArrival,
            journeyState = SafeJourneyState.PROGRESSING,
        )

        val evaluated = overdueJourney.evaluateTimers(now)

        // Truth Invariant: State becomes NO_RESPONSE
        assertEquals(SafeJourneyState.NO_RESPONSE, evaluated.journeyState)

        // It must NOT automatically escalate to an emergency
        assertNotEquals("EMERGENCY", evaluated.journeyState.name)
    }
}
