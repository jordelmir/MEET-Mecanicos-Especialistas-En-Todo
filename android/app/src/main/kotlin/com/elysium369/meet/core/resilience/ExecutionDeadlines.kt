package com.elysium369.meet.core.resilience

/**
 * MEET / ELYSIUM Execution Deadlines & Timeout Safety.
 * Enforces Master Order Section 48:
 * - Specific connect, request, and overall deadlines.
 * - Never waits indefinitely for PSP, Google, routing, or geocoding.
 * - Financial timeout maps to UNKNOWN / reconcile, NEVER to blind charge retry.
 */
object ExecutionDeadlines {
    const val CONNECT_TIMEOUT_MS = 5_000L
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val OVERALL_DEADLINE_MS = 30_000L

    const val PSP_PAYMENT_TIMEOUT_MS = 20_000L
    const val GOOGLE_PLAY_VERIFY_TIMEOUT_MS = 10_000L
    const val ROUTING_DISPATCH_TIMEOUT_MS = 8_000L

    enum class PaymentTimeoutResolution {
        RECONCILE_UNKNOWN,
        FAIL_CLOSED_NO_RETRY
    }

    fun resolvePaymentTimeout(): PaymentTimeoutResolution {
        // Master Order Section 48: timeout -> UNKNOWN -> reconcile (NOT retry charge blindly)
        return PaymentTimeoutResolution.RECONCILE_UNKNOWN
    }
}
