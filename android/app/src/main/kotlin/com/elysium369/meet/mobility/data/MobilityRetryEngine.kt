package com.elysium369.meet.mobility.data

import kotlin.random.Random

object MobilityRetryEngine {
    fun retryDelayMillis(
        attempt: Int,
        baseMs: Long = 1_000L,
        maxMs: Long = 60_000L,
    ): Long {
        val cappedAttempt = attempt.coerceIn(0, 20)
        val exponential = baseMs * (1L shl cappedAttempt.coerceAtMost(10))
        val capped = exponential.coerceAtMost(maxMs)
        val jitter = Random.nextLong(
            from = 0L,
            until = (capped / 4L).coerceAtLeast(1L),
        )
        return capped + jitter
    }
}
