package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.SafetyRetryPolicy
import org.junit.Assert.*
import org.junit.Test

class SafetyOutboxIdempotencyTest {

    @Test
    fun `Same idempotency key always produces same jitter for same attempt`() {
        val delay1 = SafetyRetryPolicy.delayMillis(attempt = 1, idempotencyKey = "key1")
        val delay2 = SafetyRetryPolicy.delayMillis(attempt = 1, idempotencyKey = "key1")
        assertEquals(delay1, delay2)
    }

    @Test
    fun `Delay increases with attempt count exponentially`() {
        val delay1 = SafetyRetryPolicy.delayMillis(attempt = 1, idempotencyKey = "key2")
        val delay2 = SafetyRetryPolicy.delayMillis(attempt = 2, idempotencyKey = "key2")
        val delay3 = SafetyRetryPolicy.delayMillis(attempt = 3, idempotencyKey = "key2")
        assertTrue(delay2 > delay1)
        assertTrue(delay3 > delay2)
    }

    @Test
    fun `Delay never exceeds 15 minutes`() {
        val delay = SafetyRetryPolicy.delayMillis(attempt = 100, idempotencyKey = "key3")
        assertTrue(delay <= 15 * 60 * 1000L)
    }

    @Test
    fun `Attempt 0 gives base delay approx 15s`() {
        val delay = SafetyRetryPolicy.delayMillis(attempt = 0, idempotencyKey = "key4")
        assertTrue(delay in 15000L..20000L)
    }
}
