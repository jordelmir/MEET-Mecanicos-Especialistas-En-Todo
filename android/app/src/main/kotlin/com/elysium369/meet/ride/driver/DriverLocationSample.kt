package com.elysium369.meet.ride.driver

import java.time.Instant

data class DriverLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAt: Instant,
    // Monotonic clock reading to measure local freshness without trusting civil wall clock
    val capturedAtElapsedRealtimeNanos: Long,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
        require(accuracyMeters.isFinite() && accuracyMeters >= 0.0) { "Accuracy must be non-negative: $accuracyMeters" }
        require(capturedAtElapsedRealtimeNanos >= 0L) { "Elapsed realtime nanos must be non-negative: $capturedAtElapsedRealtimeNanos" }
    }
}
