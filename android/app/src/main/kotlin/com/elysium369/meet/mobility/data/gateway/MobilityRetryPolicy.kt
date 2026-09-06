package com.elysium369.meet.mobility.data.gateway

import kotlin.random.Random

class MobilityRetryPolicy(
    private val random: Random = Random.Default,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 60_000L,
    private val maxAttempts: Int = 8,
) {

    fun canRetry(
        attempt: Int,
    ): Boolean = attempt < maxAttempts

    fun delayMillis(
        attempt: Int,
        retryAfterMillis: Long? = null,
    ): Long {

        retryAfterMillis?.let {
            return it.coerceIn(
                minimumValue = 0L,
                maximumValue = maxDelayMs,
            )
        }

        val exponent = attempt.coerceIn(0, 20)

        val multiplier = 1L shl exponent

        val upperBound =
            if (baseDelayMs > maxDelayMs / multiplier) {
                maxDelayMs
            } else {
                (baseDelayMs * multiplier)
                    .coerceAtMost(maxDelayMs)
            }

        if (upperBound <= 1L) {
            return 0L
        }

        return random.nextLong(
            from = 0L,
            until = upperBound + 1L,
        )
    }
}
