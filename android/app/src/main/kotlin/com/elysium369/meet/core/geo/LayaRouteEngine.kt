package com.elysium369.meet.core.geo

import java.util.Calendar
import kotlin.math.*

/**
 * ══════════════════════════════════════════════════════════════════════
 *  L A Y A   R O U T E   E N G I N E   &   E T A   P R E D I C T O R
 *  ──────────────────────────────────────────────────────────────
 *  Motor de enrutamiento y estimación de llegada (ETA) local-first.
 *  - Genera polilíneas de ruta realistas con curvatura vial y waypoints.
 *  - Modela el tráfico dinámico de Costa Rica (hora pico GAM mañana/tarde).
 *  - Compatible con CommonMapPanel, GeoRoute y CommonMapState.
 *  - Proporciona tiempo estimado (ETA) y narrativa táctica de trayecto.
 * ══════════════════════════════════════════════════════════════════════
 */
object LayaRouteEngine {

    data class RouteEstimate(
        val origin: GeoPoint,
        val destination: GeoPoint,
        val distanceKm: Double,
        val distanceMeters: Long,
        val etaMinutes: Int,
        val durationSeconds: Long,
        val trafficLevel: TrafficLevel,
        val estimatedSpeedKmh: Double,
        val geoRoute: GeoRoute,
        val tacticalAdvice: String,
    )

    enum class TrafficLevel(val label: String, val colorHex: String) {
        FLUID("Tráfico Fluido", "#00E5FF"),
        MODERATE("Tráfico Moderado", "#FFB300"),
        HEAVY("Tráfico Congestionado", "#FF3B30"),
        GRIDLOCK("Colapso Vial / Presa GAM", "#D50000"),
    }

    /**
     * Calcula la ruta con curvatura vial realista y el ETA dinámico de llegada.
     */
    fun calculateRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        minute: Int = Calendar.getInstance().get(Calendar.MINUTE),
        isEmergencyVehicle: Boolean = false,
        routeColorHex: String? = null,
    ): RouteEstimate {
        val greatCircleKm = CostaRicaGisDatabase.haversineDistanceKm(
            origin.latitude,
            origin.longitude,
            destination.latitude,
            destination.longitude
        )

        // Road tortuosity index: in Costa Rican topography, roads are ~1.28x great circle distance
        val roadWindingFactor = when {
            greatCircleKm < 2.0 -> 1.20
            greatCircleKm < 15.0 -> 1.30
            else -> 1.38
        }
        val actualRoadKm = max(0.2, greatCircleKm * roadWindingFactor)
        val distanceMeters = (actualRoadKm * 1000.0).roundToLong()

        // Traffic Model for Costa Rica (GAM Congestion Curves)
        val trafficLevel = resolveTrafficLevel(hourOfDay, isEmergencyVehicle)
        val baseSpeedKmh = when (trafficLevel) {
            TrafficLevel.FLUID -> if (actualRoadKm > 10.0) 55.0 else 38.0
            TrafficLevel.MODERATE -> 26.0
            TrafficLevel.HEAVY -> 16.0
            TrafficLevel.GRIDLOCK -> 10.0
        }
        val speedKmh = if (isEmergencyVehicle) max(40.0, baseSpeedKmh * 1.5) else baseSpeedKmh

        val travelHours = actualRoadKm / speedKmh
        val durationSeconds = max(60L, (travelHours * 3600.0).roundToLong())
        val etaMinutes = max(1, ceil(travelHours * 60.0).toInt())

        // Generate synthetic road waypoints using Bezier curvature for visual realism
        val routePoints = generateRoadWaypoints(origin, destination, actualRoadKm)

        val selectedColor = routeColorHex ?: trafficLevel.colorHex
        val geoRoute = GeoRoute(
            points = routePoints,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            routeColorHex = selectedColor,
        )

        val advice = when (trafficLevel) {
            TrafficLevel.FLUID -> "Vía libre detectada. Llegada óptima en $etaMinutes min."
            TrafficLevel.MODERATE -> "Flujo regular en corredores principales. Llegada estimada en $etaMinutes min."
            TrafficLevel.HEAVY -> "Precaución: alta densidad vehicular en el sector. ETA: $etaMinutes min."
            TrafficLevel.GRIDLOCK -> "Alerta Laya: retenciones severas en autopista/circunvalación. Especialista avanzando con precaución."
        }

        return RouteEstimate(
            origin = origin,
            destination = destination,
            distanceKm = round(actualRoadKm * 10.0) / 10.0,
            distanceMeters = distanceMeters,
            etaMinutes = etaMinutes,
            durationSeconds = durationSeconds,
            trafficLevel = trafficLevel,
            estimatedSpeedKmh = round(speedKmh * 10.0) / 10.0,
            geoRoute = geoRoute,
            tacticalAdvice = advice,
        )
    }

    private fun resolveTrafficLevel(hour: Int, isEmergency: Boolean): TrafficLevel {
        if (isEmergency) return TrafficLevel.FLUID

        // Costa Rica GAM peak hours:
        // Morning rush: 06:30 - 08:45
        // Evening rush: 16:30 - 19:15
        // Midday rush: 11:45 - 13:15
        return when (hour) {
            in 7..8 -> TrafficLevel.HEAVY
            6 -> TrafficLevel.MODERATE
            12 -> TrafficLevel.MODERATE
            in 17..18 -> TrafficLevel.GRIDLOCK
            16, 19 -> TrafficLevel.HEAVY
            in 9..11, in 13..15, 20 -> TrafficLevel.MODERATE
            else -> TrafficLevel.FLUID
        }
    }

    /**
     * Genera una polilínea vial orgánica interpolando curvatura mediante
     * proyección ortogonal y puntos de inflexión naturales.
     */
    private fun generateRoadWaypoints(origin: GeoPoint, destination: GeoPoint, distanceKm: Double): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(origin)

        // For very short distances, straight line or minimal curve
        val numSegments = when {
            distanceKm < 1.0 -> 3
            distanceKm < 5.0 -> 6
            distanceKm < 15.0 -> 10
            else -> 16
        }

        val dLat = destination.latitude - origin.latitude
        val dLon = destination.longitude - origin.longitude

        // Perpendicular vector for natural street curve simulation
        val perpLat = -dLon
        val perpLon = dLat
        val perpLen = sqrt(perpLat * perpLat + perpLon * perpLon).coerceAtLeast(0.000001)

        val maxOffsetDegrees = min(0.006, (distanceKm * 0.0007))

        for (i in 1 until numSegments) {
            val fraction = i.toDouble() / numSegments.toDouble()
            // S-curve sin wave lateral perturbation
            val lateralFactor = sin(fraction * Math.PI) * sin(fraction * Math.PI * 2.0)
            val offsetLat = (perpLat / perpLen) * maxOffsetDegrees * lateralFactor
            val offsetLon = (perpLon / perpLen) * maxOffsetDegrees * lateralFactor

            val interpLat = origin.latitude + (dLat * fraction) + offsetLat
            val interpLon = origin.longitude + (dLon * fraction) + offsetLon

            points.add(GeoPoint(interpLat, interpLon))
        }

        points.add(destination)
        return points
    }
}
