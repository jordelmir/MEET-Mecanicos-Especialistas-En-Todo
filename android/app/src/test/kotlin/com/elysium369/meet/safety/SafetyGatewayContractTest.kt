package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.SafetyGatewayResult
import org.junit.Assert.*
import org.junit.Test

class SafetyGatewayContractTest {

    @Test
    fun `Accepted has serverVersion greater than 0`() {
        val result = SafetyGatewayResult.Accepted(
            state = "SYNCED",
            serverVersion = 5L,
            correlationId = "corr-1"
        )
        assertTrue(result.serverVersion > 0)
    }

    @Test
    fun `Rejected with retryable true vs false`() {
        val retryable = SafetyGatewayResult.Rejected(
            code = "RATE_LIMITED",
            message = "Too many requests",
            correlationId = "corr-2",
            retryable = true
        )
        val nonRetryable = SafetyGatewayResult.Rejected(
            code = "BAD_REQUEST",
            message = "Bad payload",
            correlationId = "corr-3",
            retryable = false
        )
        
        assertTrue(retryable.retryable)
        assertFalse(nonRetryable.retryable)
    }

    @Test
    fun `TransportFailure captures error codes`() {
        val failure = SafetyGatewayResult.TransportFailure(code = "503", message = "Service Unavailable")
        assertEquals("503", failure.code)
        assertEquals("Service Unavailable", failure.message)
    }
}
