package com.elysium369.meet.ride.location

import java.security.MessageDigest

/**
 * Forensic-grade GPS trail recorded during a ride.
 * Each point is immutable and the trail carries a SHA-256 integrity hash
 * so authorities can verify the recording was not tampered with.
 */
data class GpsForensicPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val headingDegrees: Int?,
    val capturedAtEpochMs: Long,
    val sequence: Long,
)

data class GpsForensicTrail(
    val rideId: String,
    val driverId: String,
    val passengerId: String,
    val points: List<GpsForensicPoint>,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val integrityHash: String,
    val totalDistanceMeters: Double,
    val averageSpeedMps: Double,
    val maxSpeedMps: Double,
) {
    val pointCount: Int get() = points.size

    val durationMs: Long get() = completedAtEpochMs - startedAtEpochMs

    val durationMinutes: Int get() = (durationMs / 60_000).toInt()

    companion object {
        /**
         * Build a trail from raw points and compute the integrity hash.
         * The hash covers: rideId, all GPS coordinates in order, timestamps, and sequence numbers.
         */
        fun build(
            rideId: String,
            driverId: String,
            passengerId: String,
            points: List<GpsForensicPoint>,
        ): GpsForensicTrail {
            val sorted = points.sortedBy { it.sequence }
            val startedAt = sorted.firstOrNull()?.capturedAtEpochMs ?: System.currentTimeMillis()
            val completedAt = sorted.lastOrNull()?.capturedAtEpochMs ?: System.currentTimeMillis()

            var totalDist = 0.0
            var maxSpeed = 0.0
            var speedSum = 0.0
            var speedCount = 0
            for (i in 1 until sorted.size) {
                totalDist += haversineMeters(
                    sorted[i - 1].latitude, sorted[i - 1].longitude,
                    sorted[i].latitude, sorted[i].longitude,
                )
                sorted[i].speedMetersPerSecond?.let {
                    maxSpeed = maxOf(maxSpeed, it.toDouble())
                    speedSum += it
                    speedCount++
                }
            }

            val integrityHash = computeIntegrityHash(rideId, sorted)

            return GpsForensicTrail(
                rideId = rideId,
                driverId = driverId,
                passengerId = passengerId,
                points = sorted,
                startedAtEpochMs = startedAt,
                completedAtEpochMs = completedAt,
                integrityHash = integrityHash,
                totalDistanceMeters = totalDist,
                averageSpeedMps = if (speedCount > 0) speedSum / speedCount else 0.0,
                maxSpeedMps = maxSpeed,
            )
        }

        fun computeIntegrityHash(rideId: String, sortedPoints: List<GpsForensicPoint>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun append(value: String) {
                digest.update(value.toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
            append("meet-gps-forensic-v1")
            append(rideId)
            sortedPoints.forEach { p ->
                append(p.latitude.toString())
                append(p.longitude.toString())
                append(p.capturedAtEpochMs.toString())
                append(p.sequence.toString())
                append(p.accuracyMeters.toString())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.pow(Math.sin(dLat / 2), 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.pow(Math.sin(dLon / 2), 2.0)
            return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }
    }
}
