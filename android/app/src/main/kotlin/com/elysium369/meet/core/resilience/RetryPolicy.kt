package com.elysium369.meet.core.resilience

import com.elysium369.meet.core.error.DomainFailure
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * MEET / ELYSIUM Resilience Retry Policy.
 * Enforces Master Order Section 47:
 * - Bounded retries
 * - Exponential backoff with jitter
 * - Strictly retries retryable dependencies only
 * - NEVER retries CARD_DECLINED, INVALID_TOKEN, FORBIDDEN, INVALID_STATE, INSUFFICIENT_FUNDS
 */
class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 200L,
    val maxDelayMs: Long = 3000L,
    val backoffMultiplier: Double = 2.0,
    private val random: Random = Random.Default
) {
    companion object {
        val TERMINAL_FAILURE_CODES = setOf(
            "CARD_DECLINED",
            "INVALID_TOKEN",
            "FORBIDDEN",
            "INVALID_STATE",
            "INSUFFICIENT_FUNDS",
            "PURCHASE_ALREADY_CLAIMED",
            "RIDE_ALREADY_ACCEPTED",
            "VERSION_CONFLICT",
            "ACTIVE_COMMITMENTS_EXIST"
        )
    }

    fun isRetryable(failure: DomainFailure): Boolean {
        return when (failure) {
            is DomainFailure.RetryableDependency -> true
            is DomainFailure.RateLimit -> true
            is DomainFailure.TerminalDependency -> {
                failure.code !in TERMINAL_FAILURE_CODES && false
            }
            is DomainFailure.Conflict,
            is DomainFailure.Unauthenticated,
            is DomainFailure.Forbidden,
            is DomainFailure.Validation,
            is DomainFailure.Integrity,
            is DomainFailure.Protocol,
            is DomainFailure.Internal -> false
        }
    }

    fun computeDelayMs(attempt: Int): Long {
        if (attempt <= 1) return initialDelayMs
        val calculated = initialDelayMs * backoffMultiplier.pow((attempt - 1).toDouble()).toLong()
        val bounded = min(calculated, maxDelayMs)
        // Add full jitter between 0 and bounded delay
        val jitter = random.nextLong(0, (bounded * 0.25).toLong().coerceAtLeast(1L))
        return bounded + jitter
    }
}
