package com.elysium369.meet.safety.geo

import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoArea
import com.elysium369.meet.core.geo.GeoBounds
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.safety.data.SafetyPrivateMapPoint

object SafetyMapAdapter {

    /**
     * @param privateLabel Localized label for the driver's own private reports (e.g., "Mi reporte").
     * @param independentSourcesSuffix Localized suffix for source count (e.g., " fuentes independientes").
     */
    fun build(
        points: List<SafetyPublicPoint>,
        privatePoints: List<SafetyPrivateMapPoint> = emptyList(),
        privateLabel: String = "Mi reporte",
        independentSourcesSuffix: String = " fuentes independientes",
    ): CommonMapState {
        val publicAreas = points.filter {
            it.geoDisclosure == "COARSE_GRID_25KM_PLUS" && (it.uncertaintyMeters ?: 0) >= 25_000
        }.map { point ->
            val radius = requireNotNull(point.uncertaintyMeters)
            val latitudeSpan = radius / 111_000.0
            val longitudeSpan = latitudeSpan / kotlin.math.cos(Math.toRadians(point.displayLatitude)).coerceAtLeast(0.1)
            val north = (point.displayLatitude + latitudeSpan).coerceAtMost(90.0)
            val south = (point.displayLatitude - latitudeSpan).coerceAtLeast(-90.0)
            val east = (point.displayLongitude + longitudeSpan).coerceAtMost(180.0)
            val west = (point.displayLongitude - longitudeSpan).coerceAtLeast(-180.0)
            GeoArea(
                point.publicPointId,
                listOf(GeoPoint(north, west), GeoPoint(north, east), GeoPoint(south, east), GeoPoint(south, west)),
                point.label,
                radius,
            )
        }
        val publicMarkers = points.mapNotNull { point ->
            if (!point.displayLatitude.isFinite() || !point.displayLongitude.isFinite()) return@mapNotNull null
            if (point.geoDisclosure != "EXACT_GEOLOCATED") return@mapNotNull null
            val role = if (point.category == "HOMICIDE") GeoMarkerRole.HOMICIDE_PIN else GeoMarkerRole.INCIDENT_PIN
            GeoMarker(
                id = point.publicPointId,
                role = role,
                point = GeoPoint(point.displayLatitude, point.displayLongitude, point.uncertaintyMeters?.toFloat()),
                label = point.label.ifBlank { point.category },
                subtitle = "${point.independentSourceCount}$independentSourcesSuffix",
                iconResName = point.category,
                isHighlighted = false,
            )
        }
        val privateMarkers = privatePoints.map { point ->
            val role = if (point.category == "HOMICIDE") GeoMarkerRole.HOMICIDE_PIN else GeoMarkerRole.PRIVATE_INCIDENT_PIN
            GeoMarker(
                id = point.markerId,
                role = role,
                point = GeoPoint(point.latitude, point.longitude, point.accuracyMeters, point.occurredAt),
                label = privateLabel,
                subtitle = point.serverState ?: point.syncState,
                iconResName = point.category,
                isHighlighted = point.syncState != "SYNCED",
            )
        }
        val privateReportIds = privatePoints.map { it.reportId }.toSet()
        val deduplicatedPublicMarkers = publicMarkers.filter { it.id !in privateReportIds }
        val markers = deduplicatedPublicMarkers + privateMarkers

        val bounds = GeoBounds.fromPoints(markers.map { it.point } + publicAreas.flatMap { it.boundary })

        return CommonMapState(
            markers = markers,
            areas = publicAreas,
            cameraIntent = when {
                bounds != null && (publicAreas.isNotEmpty() || markers.size > 1) ->
                    MapCameraIntent.FitBounds(bounds)
                markers.isNotEmpty() ->
                    MapCameraIntent.CenterOn(markers.first().point, 14.0)
                else ->
                    MapCameraIntent.FollowUser
            },
            showRecenterButton = false,
        )
    }
}

data class SafetyPublicPoint(
    val publicPointId: String,
    val displayLatitude: Double,
    val displayLongitude: Double,
    val label: String,
    val claimState: String,
    val independentSourceCount: Int,
    val category: String,
    val geoDisclosure: String = "WITHHELD",
    val uncertaintyMeters: Int? = null,
)
