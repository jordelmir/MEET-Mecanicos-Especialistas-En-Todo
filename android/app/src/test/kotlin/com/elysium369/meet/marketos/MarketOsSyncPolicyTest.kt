package com.elysium369.meet.marketos

import com.elysium369.meet.platform.marketos.work.MarketOsSyncWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketOsSyncPolicyTest {
    @Test
    fun retryDelayIsDeterministicBoundedAndMonotonicByAttempt() {
        val key = "10000000-0000-0000-0000-000000000001"
        val delays = (1..12).map { MarketOsSyncWorker.retryDelay(it, key) }
        assertEquals(delays, (1..12).map { MarketOsSyncWorker.retryDelay(it, key) })
        assertTrue(delays.zipWithNext().all { (left, right) -> right >= left })
        assertTrue(delays.all { it in 15_000L..900_000L })
        assertEquals(900_000L, delays.last())
    }

    @Test
    fun differentCommandsReceiveStableJitter() {
        val first = MarketOsSyncWorker.retryDelay(3, "command-a")
        val second = MarketOsSyncWorker.retryDelay(3, "command-b")
        assertTrue(first in 60_000L..90_000L)
        assertTrue(second in 60_000L..90_000L)
        assertTrue(first != second)
    }
}
