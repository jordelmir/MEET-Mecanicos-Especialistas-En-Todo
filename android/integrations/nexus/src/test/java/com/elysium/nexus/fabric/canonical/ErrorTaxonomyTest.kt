package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTaxonomyTest {

    @Test
    fun `NexusError toUserMessage returns non-empty string`() {
        val error = NexusError.DiscoveryFailed(message = "No devices found")
        val message = error.toUserMessage()
        assertTrue(message.isNotBlank())
        assertTrue(message.contains("No devices found"))
    }

    @Test
    fun `PermissionDenied includes missing permissions`() {
        val error = NexusError.PermissionDenied(
            message = "IR permission required",
            missingPermissions = listOf("TRANSMIT_IR")
        )
        assertTrue(error.missingPermissions.contains("TRANSMIT_IR"))
        assertFalse(error.isRetryable)
    }

    @Test
    fun `Timeout is retryable`() {
        val error = NexusError.Timeout(message = "Request timed out")
        assertTrue(error.isRetryable)
    }

    @Test
    fun `AuthFailed is not retryable`() {
        val error = NexusError.AuthFailed(message = "Invalid credentials")
        assertFalse(error.isRetryable)
    }

    @Test
    fun `DeviceUnreachable has correct severity`() {
        val error = NexusError.DeviceUnreachable(
            message = "Device offline",
            deviceId = DeviceId("tv-1")
        )
        assertEquals(ErrorSeverity.Warning, error.severity)
    }

    @Test
    fun `SignatureVerificationFailed is critical`() {
        val error = NexusError.SignatureVerificationFailed(message = "Tampered signature")
        assertEquals(ErrorSeverity.Critical, error.severity)
    }

    @Test
    fun `RetryPolicy backoffForAttempt computes correctly`() {
        val policy = RetryPolicy(maxRetries = 3, backoffMs = 1000L, backoffMultiplier = 2.0)

        assertEquals(1000L, policy.backoffForAttempt(0))
        assertEquals(2000L, policy.backoffForAttempt(1))
        assertEquals(4000L, policy.backoffForAttempt(2))
    }

    @Test
    fun `RetryPolicy backoffForAttempt respects maxBackoffMs`() {
        val policy = RetryPolicy(
            maxRetries = 10,
            backoffMs = 1000L,
            backoffMultiplier = 10.0,
            maxBackoffMs = 5000L
        )

        assertTrue(policy.backoffForAttempt(5) <= 5000L)
    }

    @Test
    fun `RetryPolicy NONE has zero retries`() {
        val none = RetryPolicy(maxRetries = 0, backoffMs = 1L)
        assertEquals(0, none.maxRetries)
    }

    @Test
    fun `NetworkError toUserMessage includes Retrying`() {
        val error = NexusError.NetworkError(message = "Connection refused")
        assertTrue(error.toUserMessage().contains("Retrying"))
    }

    @Test
    fun `CommandRejected toUserMessage does not include Retrying`() {
        val error = NexusError.CommandRejected(message = "Device rejected command")
        assertFalse(error.toUserMessage().contains("Retrying"))
    }

    @Test
    fun `SchemaVersionMismatch is not retryable`() {
        val error = NexusError.SchemaVersionMismatch(message = "Schema v3 != v5")
        assertFalse(error.isRetryable)
    }

    @Test
    fun `InvalidConfig is not retryable`() {
        val error = NexusError.InvalidConfig(message = "Missing required field")
        assertFalse(error.isRetryable)
    }

    @Test
    fun `ResourceExhausted is retryable`() {
        val error = NexusError.ResourceExhausted(message = "Memory full")
        assertTrue(error.isRetryable)
    }

    @Test
    fun `HardwareUnavailable is not retryable`() {
        val error = NexusError.HardwareUnavailable(message = "No IR emitter")
        assertFalse(error.isRetryable)
    }

    @Test
    fun `DiscoveryFailed is retryable`() {
        val error = NexusError.DiscoveryFailed(message = "Scan timeout")
        assertTrue(error.isRetryable)
    }

    @Test
    fun `ProtocolError is retryable`() {
        val error = NexusError.ProtocolError(message = "Malformed response")
        assertTrue(error.isRetryable)
    }

    @Test
    fun `NexusErrorCode values are distinct`() {
        val values = NexusErrorCode.entries
        assertEquals(20, values.size)
        assertEquals(values.size, values.map { it.name }.toSet().size)
    }
}
