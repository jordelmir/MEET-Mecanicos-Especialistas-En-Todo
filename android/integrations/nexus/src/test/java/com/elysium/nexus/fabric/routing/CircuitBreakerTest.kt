package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.canonical.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CircuitBreakerTest {

    private lateinit var breaker: CircuitBreaker

    @Before
    fun setup() {
        breaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 1000L)
    }

    @Test
    fun `allowAttempt returns true for new protocol`() {
        assertTrue(breaker.allowAttempt(Protocol.DirectIr))
    }

    @Test
    fun `allowAttempt returns true below threshold`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        assertTrue(breaker.allowAttempt(Protocol.DirectIr))
    }

    @Test
    fun `allowAttempt returns false at threshold`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        assertFalse(breaker.allowAttempt(Protocol.DirectIr))
    }

    @Test
    fun `recordSuccess resets failure count`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordSuccess(Protocol.DirectIr)

        val state = breaker.stateFor(Protocol.DirectIr)
        assertEquals(0, state.consecutiveFailures)
        assertEquals(CircuitStatus.Closed, state.status)
    }

    @Test
    fun `recordSuccess in half-open closes circuit`() {
        // Trip the breaker with a very short cooldown
        val shortBreaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 1L)

        shortBreaker.recordFailure(Protocol.DirectIr)
        shortBreaker.recordFailure(Protocol.DirectIr)
        shortBreaker.recordFailure(Protocol.DirectIr)
        assertFalse(shortBreaker.allowAttempt(Protocol.DirectIr))

        // Wait for cooldown to expire (1ms)
        Thread.sleep(2)

        // allowAttempt transitions to half-open
        assertTrue(shortBreaker.allowAttempt(Protocol.DirectIr))

        // recordSuccess in half-open closes the circuit
        shortBreaker.recordSuccess(Protocol.DirectIr)
        val state = shortBreaker.stateFor(Protocol.DirectIr)
        assertEquals(CircuitStatus.Closed, state.status)
        assertEquals(0, state.consecutiveFailures)
    }

    @Test
    fun `recordFailure in half-open reopens circuit`() {
        // Trip the breaker
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)

        // Force to half-open
        breaker.recordSuccess(Protocol.DirectIr) // closes
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr) // opens again

        val state = breaker.stateFor(Protocol.DirectIr)
        assertEquals(CircuitStatus.Open, state.status)
    }

    @Test
    fun `reset clears protocol circuit`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)

        breaker.reset(Protocol.DirectIr)
        assertTrue(breaker.allowAttempt(Protocol.DirectIr))
    }

    @Test
    fun `resetAll clears all circuits`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.WiFi)

        breaker.resetAll()
        assertTrue(breaker.allowAttempt(Protocol.DirectIr))
        assertTrue(breaker.allowAttempt(Protocol.WiFi))
    }

    @Test
    fun `different protocols have independent circuits`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)

        assertFalse(breaker.allowAttempt(Protocol.DirectIr))
        assertTrue(breaker.allowAttempt(Protocol.WiFi))
    }

    @Test
    fun `stateFor returns closed state for unknown protocol`() {
        val state = breaker.stateFor(Protocol.Unknown)
        assertEquals(CircuitStatus.Closed, state.status)
        assertEquals(0, state.consecutiveFailures)
    }

    @Test
    fun `isBlocking is true when open`() {
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)

        assertTrue(breaker.stateFor(Protocol.DirectIr).isBlocking)
    }

    @Test
    fun `isBlocking is false when closed`() {
        assertFalse(breaker.stateFor(Protocol.DirectIr).isBlocking)
    }
}
