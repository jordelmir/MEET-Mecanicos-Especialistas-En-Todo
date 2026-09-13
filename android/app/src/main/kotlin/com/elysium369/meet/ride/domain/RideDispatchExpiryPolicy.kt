package com.elysium369.meet.ride.domain

/** Shared client boundary for the server's 30-minute dispatch lease. */
object RideDispatchExpiryPolicy {
    const val LEASE_MILLIS: Long = 30L * 60L * 1_000L

    fun remainsVisible(createdAtEpochMs: Long, nowEpochMs: Long): Boolean =
        createdAtEpochMs > 0L && nowEpochMs >= createdAtEpochMs &&
            nowEpochMs - createdAtEpochMs < LEASE_MILLIS

    fun recommendedOpenBidMinor(currentMinor: Long, currency: String): Long =
        RideFareBidPolicy.adjustMinor(currentMinor.coerceAtLeast(0L), currency, 1)
}
