package com.elysium369.meet.ride.eta

import javax.inject.Inject
import kotlin.math.*

/**
 * Geometric fallback ETA provider using Haversine distance, urban road winding factor (1.3x),
 * and realistic urban average speeds (25 km/h) with limited confidence labeling.
 */
class FallbackEtaProvider @Inject constructor() : RideEtaProvider {

    override suspend fun calculateEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): Result<RideEtaEstimate> = runCatching {
        val straightDistanceMeters = haversineDistanceMeters(originLat, originLon, destLat, destLon)
        // 1.30 winding factor for typical urban streets
        val estimatedRoadDistanceMeters = (straightDistanceMeters * 1.30).roundToInt()
        
        // Urban speed ~ 25 km/h = ~6.94 m/s
        val speedMps = 6.94
        val baseEtaSeconds = (estimatedRoadDistanceMeters / speedMps).roundToInt()
        // Add 60s minimum for traffic lights/turns
        val totalEtaSeconds = (baseEtaSeconds + 60).coerceAtLeast(60)

        RideEtaEstimate(
            etaSeconds = totalEtaSeconds,
            distanceMeters = estimatedRoadDistanceMeters,
            sourceRaw = RideEtaSource.HAVERSINE_FALLBACK.code,
            confidence = 0.500,
            trafficCondition = "UNKNOWN",
        )
    }

    private fun haversineDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
