package com.elysium369.meet.ride.nextjob

import org.junit.Assert.*
import org.junit.Test

class NextJobSchedulerTest {

    @Test
    fun returns_not_delayed_when_within_tolerated_window() {
        val expectedFinish = 100_000L
        val current = 120_000L // 20s elapsed

        val evaluation = NextJobScheduler.evaluateDelay(
            expectedFinishTimestampMs = expectedFinish,
            currentTimeMs = current,
            maxToleratedDelaySeconds = 300, // 5 min
        )

        assertFalse(evaluation.isDelayed)
        assertEquals(20, evaluation.delaySeconds)
        assertNull(evaluation.passengerActionRecommended)
    }

    @Test
    fun flags_delayed_and_recommends_passenger_action_when_exceeding_threshold() {
        val expectedFinish = 100_000L
        val current = 500_000L // 400s elapsed > 300s threshold

        val evaluation = NextJobScheduler.evaluateDelay(
            expectedFinishTimestampMs = expectedFinish,
            currentTimeMs = current,
            maxToleratedDelaySeconds = 300,
        )

        assertTrue(evaluation.isDelayed)
        assertEquals(400, evaluation.delaySeconds)
        assertEquals("OFFER_RESELECT_OR_WAIT", evaluation.passengerActionRecommended)
    }
}
