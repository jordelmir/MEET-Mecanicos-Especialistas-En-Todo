package com.elysium369.meet.safety.geo

import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoBounds
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.MapCameraIntent

object SafetyMapAdapter {

    fun build(
        points: List<SafetyPublicPoint>,
    ): CommonMapState {
        val markers = points.map { point ->
            GeoMarker(
                id = point.publicPointId,
                role = GeoMarkerRole.INCIDENT_PIN,
                point = GeoPoint(
                    latitude = point.displayLatitude,
                    longitude = point.displayLongitude,
                ),
                label = point.label,
                subtitle = buildString {
                    append(point.claimState)
                    if (point.independentSourceCount > 0) {
                        append(" · ")
                        append(point.independentSourceCount)
                        append(" fuentes independientes")
                    }
                },
            )
        }

        val bounds = GeoBounds.fromPoints(markers.map { it.point })

        return CommonMapState(
            markers = markers,
            cameraIntent = when {
                bounds != null && markers.size > 1 ->
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
)
