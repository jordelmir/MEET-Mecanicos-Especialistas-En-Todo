package com.elysium369.meet.ride.nextjob

/**
 * Pure domain logic evaluating chained dispatch transitions and delay thresholds.
 */
object NextJobScheduler {
    const val DEFAULT_MAX_DELAY_SECONDS = 300 // 5 minutes

    data class DelayEvaluation(
        val isDelayed: Boolean,
        val delaySeconds: Int,
        val passengerActionRecommended: String?,
    )

    fun evaluateDelay(
        expectedFinishTimestampMs: Long,
        currentTimeMs: Long,
        maxToleratedDelaySeconds: Int = DEFAULT_MAX_DELAY_SECONDS,
    ): DelayEvaluation {
        val delayMs = currentTimeMs - expectedFinishTimestampMs
        val delaySeconds = (delayMs / 1000L).toInt()

        return if (delaySeconds > maxToleratedDelaySeconds) {
            DelayEvaluation(
                isDelayed = true,
                delaySeconds = delaySeconds,
                passengerActionRecommended = "OFFER_RESELECT_OR_WAIT",
            )
        } else {
            DelayEvaluation(
                isDelayed = false,
                delaySeconds = delaySeconds.coerceAtLeast(0),
                passengerActionRecommended = null,
            )
        }
    }
}
