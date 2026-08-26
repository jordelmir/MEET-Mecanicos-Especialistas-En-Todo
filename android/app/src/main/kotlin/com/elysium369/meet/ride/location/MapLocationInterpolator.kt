package com.elysium369.meet.ride.location

import kotlin.math.*

/**
 * Pure domain location interpolator with bearing smoothing and strict staleness cutoff.
 * Adheres strictly to the truth doctrine: If GPS is stale (> 15s), animation stops and
 * staleness is displayed explicitly rather than fabricating motion.
 */
object MapLocationInterpolator {
    const val MAX_ANIMATION_AGE_MS = 15_000L // 15 seconds

    data class InterpolatedPoint(
        val latitude: Double,
        val longitude: Double,
        val bearingDegrees: Float,
        val isStale: Boolean,
        val ageSeconds: Long,
        val stalenessDisplay: String,
    )

    data class LocationPoint(
        val latitude: Double,
        val longitude: Double,
        val bearing: Float?,
        val timestampMs: Long,
    )

    fun interpolate(
        from: LocationPoint,
        to: LocationPoint,
        currentRenderTimeMs: Long,
    ): InterpolatedPoint {
        val ageMs = currentRenderTimeMs - to.timestampMs
        val ageSeconds = (ageMs / 1000L).coerceAtLeast(0L)
        val isStale = ageMs > MAX_ANIMATION_AGE_MS

        val stalenessDisplay = when {
            ageSeconds <= 3 -> "Ubicación en vivo"
            else -> "Ubicación actualizada hace ${ageSeconds}s"
        }

        if (isStale) {
            // Stop animating at latest known position
            return InterpolatedPoint(
                latitude = to.latitude,
                longitude = to.longitude,
                bearingDegrees = to.bearing ?: computeBearing(from.latitude, from.longitude, to.latitude, to.longitude),
                isStale = true,
                ageSeconds = ageSeconds,
                stalenessDisplay = stalenessDisplay,
            )
        }

        val totalDurationMs = (to.timestampMs - from.timestampMs).coerceAtLeast(1L)
        val elapsedMs = (currentRenderTimeMs - from.timestampMs).coerceIn(0L, totalDurationMs)
        val fraction = (elapsedMs.toDouble() / totalDurationMs.toDouble()).coerceIn(0.0, 1.0)

        val lat = from.latitude + (to.latitude - from.latitude) * fraction
        val lng = from.longitude + (to.longitude - from.longitude) * fraction

        val startBearing = from.bearing ?: computeBearing(from.latitude, from.longitude, to.latitude, to.longitude)
        val targetBearing = to.bearing ?: computeBearing(from.latitude, from.longitude, to.latitude, to.longitude)
        val interpolatedBearing = interpolateBearing(startBearing, targetBearing, fraction.toFloat())

        return InterpolatedPoint(
            latitude = lat,
            longitude = lng,
            bearingDegrees = interpolatedBearing,
            isStale = false,
            ageSeconds = ageSeconds,
            stalenessDisplay = stalenessDisplay,
        )
    }

    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return ((bearingDeg + 360) % 360).toFloat()
    }

    private fun interpolateBearing(fromDeg: Float, toDeg: Float, fraction: Float): Float {
        var diff = (toDeg - fromDeg) % 360
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        return ((fromDeg + diff * fraction) + 360) % 360
    }
}
