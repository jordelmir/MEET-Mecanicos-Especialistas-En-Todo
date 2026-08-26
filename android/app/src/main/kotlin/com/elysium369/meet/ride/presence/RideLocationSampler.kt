package com.elysium369.meet.ride.presence

import kotlin.math.*

object RideLocationSampler {
    data class LocationSample(
        val latitude: Double,
        val longitude: Double,
        val accuracyM: Float,
        val heading: Int?,
        val speedMps: Float?,
        val timestampMs: Long,
    )

    fun intervalMs(availability: RideDriverAvailability): Long {
        return when (availability) {
            RideDriverAvailability.AVAILABLE -> 8000L
            RideDriverAvailability.OFFERING -> 4000L
            RideDriverAvailability.EN_ROUTE_TO_PICKUP,
            RideDriverAvailability.IN_TRIP -> 2000L
            RideDriverAvailability.PICKUP_WAITING,
            RideDriverAvailability.FINISHING_CURRENT_TRIP -> 5000L
            RideDriverAvailability.PAUSED -> 15000L
            RideDriverAvailability.RESERVED -> 4000L
            RideDriverAvailability.OFFLINE,
            RideDriverAvailability.SUSPENDED,
            RideDriverAvailability.STALE -> Long.MAX_VALUE
        }
    }

    fun shouldReport(
        previous: LocationSample?,
        current: LocationSample,
        availability: RideDriverAvailability,
    ): Boolean {
        if (previous == null) return true

        val timeElapsedMs = current.timestampMs - previous.timestampMs
        if (timeElapsedMs >= intervalMs(availability)) return true

        val distanceM = calculateHaversineDistance(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude
        )

        val thresholdM = if (availability == RideDriverAvailability.IN_TRIP) 20.0 else 50.0
        if (distanceM > thresholdM) return true

        if (previous.heading != null && current.heading != null) {
            val headingDiff = min(
                abs(current.heading - previous.heading),
                360 - abs(current.heading - previous.heading)
            )
            if (headingDiff > 30) return true
        }

        return false
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
