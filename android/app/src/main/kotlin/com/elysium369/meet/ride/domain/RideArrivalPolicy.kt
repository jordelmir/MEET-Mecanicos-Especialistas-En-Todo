package com.elysium369.meet.ride.domain

import com.elysium369.meet.ride.map.RideGeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RideArrivalDecision(
    val allowed: Boolean,
    val distanceMeters: Double?,
    val reason: String,
)

object RideArrivalPolicy {
    const val MAX_DISTANCE_METERS = 100.0
    const val MAX_DRIVER_ACCURACY_METERS = 75f
    const val MAX_LOCATION_AGE_MS = 30_000L

    fun evaluate(
        driver: RideGeoPoint?,
        pickup: RideGeoPoint,
        nowEpochMs: Long,
    ): RideArrivalDecision {
        if (driver == null) return RideArrivalDecision(false, null, "Esperando GPS del conductor")
        if (driver.accuracyMeters == null || driver.accuracyMeters > MAX_DRIVER_ACCURACY_METERS) {
            return RideArrivalDecision(false, null, "Mejora la precisión GPS para confirmar llegada")
        }
        if (nowEpochMs - driver.capturedAtEpochMs !in 0..MAX_LOCATION_AGE_MS) {
            return RideArrivalDecision(false, null, "Actualiza tu ubicación antes de confirmar llegada")
        }
        val distance = distanceMeters(driver, pickup)
        return if (distance <= MAX_DISTANCE_METERS) {
            RideArrivalDecision(true, distance, "Dentro de la zona segura de recogida")
        } else {
            RideArrivalDecision(
                false,
                distance,
                "Acércate a 100 m o menos del pin de recogida",
            )
        }
    }

    fun distanceMeters(a: RideGeoPoint, b: RideGeoPoint): Double {
        val radius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val deltaLat = Math.toRadians(b.latitude - a.latitude)
        val deltaLon = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return radius * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }
}
