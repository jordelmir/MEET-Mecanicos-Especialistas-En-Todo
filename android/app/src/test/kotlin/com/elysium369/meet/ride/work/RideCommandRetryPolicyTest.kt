package com.elysium369.meet.ride.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCommandRetryPolicyTest {
    @Test
    fun `delay grows deterministically and remains capped`() {
        val key = "claim:retry-policy:000001"
        val delays = (1..12).map {
            RideCommandRetryPolicy.delayMillis(it, key)
        }

        assertEquals(delays, (1..12).map {
            RideCommandRetryPolicy.delayMillis(it, key)
        })
        assertTrue(delays.zipWithNext().all { (left, right) -> right >= left })
        assertTrue(delays.all { it in 15_000L..900_000L })
        assertEquals(900_000L, delays.last())
    }

    @Test
    fun `different keys receive bounded stable jitter`() {
        val first = RideCommandRetryPolicy.delayMillis(
            2,
            "claim:retry-policy:000001",
        )
        val second = RideCommandRetryPolicy.delayMillis(
            2,
            "claim:retry-policy:000002",
        )

        assertTrue(first in 30_000L until 37_500L)
        assertTrue(second in 30_000L until 37_500L)
        assertTrue(first != second)
    }
}
