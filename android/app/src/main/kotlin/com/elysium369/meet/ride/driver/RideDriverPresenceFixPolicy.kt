package com.elysium369.meet.ride.driver

object RideDriverPresenceFixPolicy {
    fun mayPublish(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        capturedAtEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        accuracyMeters > 0f && accuracyMeters <= 100f &&
        capturedAtEpochMs in 1L..nowEpochMs &&
        nowEpochMs - capturedAtEpochMs <= 60_000L
}
