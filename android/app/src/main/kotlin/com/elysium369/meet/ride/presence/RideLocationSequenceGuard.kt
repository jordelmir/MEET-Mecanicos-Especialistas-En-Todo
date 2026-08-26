package com.elysium369.meet.ride.presence

import kotlin.math.*

object RideLocationSequenceGuard {
    data class ValidationResult(
        val valid: Boolean,
        val reason: String? = null,
    )

    fun validate(
        previousSeq: Long,
        newSeq: Long,
        previousTimestampMs: Long?,
        newTimestampMs: Long,
        previousLat: Double?,
        previousLon: Double?,
        newLat: Double,
        newLon: Double,
    ): ValidationResult {
        if (newSeq <= previousSeq) {
            return ValidationResult(false, "Stale sequence: newSeq ($newSeq) <= previousSeq ($previousSeq)")
        }

        if (previousTimestampMs != null && newTimestampMs <= previousTimestampMs) {
            return ValidationResult(false, "Time travel: newTimestampMs ($newTimestampMs) <= previousTimestampMs ($previousTimestampMs)")
        }

        if (newLat < -90.0 || newLat > 90.0 || newLon < -180.0 || newLon > 180.0) {
            return ValidationResult(false, "Invalid coordinates: ($newLat, $newLon)")
        }

        if (previousLat != null && previousLon != null && previousTimestampMs != null) {
            val timeDeltaSeconds = (newTimestampMs - previousTimestampMs) / 1000.0
            if (timeDeltaSeconds > 0) {
                val distanceMeters = calculateHaversineDistance(previousLat, previousLon, newLat, newLon)
                val speedMps = distanceMeters / timeDeltaSeconds
                // 300 km/h is ~83.3 m/s
                if (speedMps > 83.33) {
                    return ValidationResult(false, "Impossible speed: ${speedMps} m/s")
                }
            }
        }

        return ValidationResult(true)
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
