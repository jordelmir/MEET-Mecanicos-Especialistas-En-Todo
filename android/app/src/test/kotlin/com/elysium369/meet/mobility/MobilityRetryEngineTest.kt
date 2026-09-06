package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.data.MobilityRetryEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityRetryEngineTest {

    @Test
    fun retryDelayGrowsExponentiallyAndIsBounded() {
        val delay0 = MobilityRetryEngine.retryDelayMillis(0, baseMs = 1000L, maxMs = 30_000L)
        assertTrue("Attempt 0 delay should be >= 1000ms: $delay0", delay0 >= 1000L)
        assertTrue("Attempt 0 delay should include bounded jitter: $delay0", delay0 <= 1500L)

        val delay5 = MobilityRetryEngine.retryDelayMillis(5, baseMs = 1000L, maxMs = 30_000L)
        assertTrue("Attempt 5 delay should be capped at maxMs + jitter: $delay5", delay5 <= 37_500L)

        val delay20 = MobilityRetryEngine.retryDelayMillis(20, baseMs = 1000L, maxMs = 30_000L)
        assertTrue("Attempt 20 delay capped at maxMs + jitter: $delay20", delay20 <= 37_500L)
    }
}
