package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.map.RideGeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ArrivalPolicy(
    val maxLocationAgeMillis: Long = 15_000L,
    val maxAccuracyMeters: Double = 50.0,
    val maxDistanceMeters: Double = 120.0,
)

sealed interface ArrivalPreflightResult {
    data object Allowed : ArrivalPreflightResult
    data class StaleLocation(
        val ageMillis: Long,
    ) : ArrivalPreflightResult
    data class PoorAccuracy(
        val accuracyMeters: Double,
    ) : ArrivalPreflightResult
    data class TooFar(
        val distanceMeters: Double,
    ) : ArrivalPreflightResult
}

object DriverArrivalPreflight {
    fun evaluate(
        location: DriverLocationSample,
        pickup: RideGeoPoint,
        nowElapsedRealtimeNanos: Long,
        policy: ArrivalPolicy = ArrivalPolicy(),
    ): ArrivalPreflightResult {
        val ageNanos = (nowElapsedRealtimeNanos - location.capturedAtElapsedRealtimeNanos)
            .coerceAtLeast(0L)
        val ageMillis = ageNanos / 1_000_000L
        if (ageMillis > policy.maxLocationAgeMillis) {
            return ArrivalPreflightResult.StaleLocation(ageMillis)
        }
        if (location.accuracyMeters > policy.maxAccuracyMeters) {
            return ArrivalPreflightResult.PoorAccuracy(
                location.accuracyMeters,
            )
        }
        val distance = haversineMeters(
            location.latitude,
            location.longitude,
            pickup.latitude,
            pickup.longitude,
        )
        if (distance > policy.maxDistanceMeters) {
            return ArrivalPreflightResult.TooFar(distance)
        }
        return ArrivalPreflightResult.Allowed
    }

    internal fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusMeters = 6_371_008.8
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2.0 * earthRadiusMeters * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
