package com.elysium369.meet.core.geo.adapters

import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoBounds
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.GeoRoute
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapState
import com.elysium369.meet.ride.map.RideMarkerRole
import kotlin.math.*

/**
 * Geometric distance calculation (Haversine formula).
 */
internal fun calculateHaversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Long {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLng = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c).roundToLong()
}

object RideMapAdapter {
    fun toCommonState(rideState: RideMapState): CommonMapState {
        val markers = rideState.markers.map { marker ->
            GeoMarker(
                id = marker.id,
                role = when (marker.role) {
                    RideMarkerRole.PASSENGER_GPS -> GeoMarkerRole.USER_LOCATION
                    RideMarkerRole.PICKUP -> GeoMarkerRole.VEHICLE_ORIGIN
                    RideMarkerRole.STOP, RideMarkerRole.DESTINATION -> GeoMarkerRole.DESTINATION
                    RideMarkerRole.DRIVER -> GeoMarkerRole.PROVIDER_LIVE
                    RideMarkerRole.ROAD_INCIDENT -> GeoMarkerRole.INCIDENT_PIN
                },
                point = GeoPoint(
                    latitude = marker.point.latitude,
                    longitude = marker.point.longitude,
                    accuracyMeters = marker.point.accuracyMeters,
                    capturedAtEpochMs = marker.point.capturedAtEpochMs,
                ),
                label = marker.label,
            )
        }
        val routes = if (rideState.route.size >= 2) {
            val pts = rideState.route.map {
                GeoPoint(it.latitude, it.longitude, it.accuracyMeters, it.capturedAtEpochMs)
            }
            val dist = calculateHaversineDistanceMeters(pts.first(), pts.last())
            val etaSec = (dist / 11.1).roundToLong() // ~40 km/h urban speed
            listOf(
                GeoRoute(
                    points = pts,
                    distanceMeters = dist,
                    durationSeconds = etaSec,
                    routeColorHex = "#00E5FF",
                )
            )
        } else emptyList()

        val bounds = GeoBounds.fromPoints(markers.map { it.point } + routes.flatMap { it.points })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = routes,
            cameraIntent = cameraIntent,
        )
    }
}

object RepairMapAdapter {
    fun buildMapState(
        userPoint: GeoPoint?,
        vehiclePoint: GeoPoint?,
        mechanicPoint: GeoPoint?,
        workshopPoint: GeoPoint?,
        workshopName: String? = null,
        mechanicName: String? = null,
    ): CommonMapState {
        val markers = mutableListOf<GeoMarker>()
        val routes = mutableListOf<GeoRoute>()

        userPoint?.let {
            markers.add(GeoMarker("user_loc", GeoMarkerRole.USER_LOCATION, it, "Mi Ubicación"))
        }
        vehiclePoint?.let {
            markers.add(GeoMarker("veh_loc", GeoMarkerRole.VEHICLE_ORIGIN, it, "Vehículo a Reparar", isHighlighted = true))
        }
        mechanicPoint?.let {
            markers.add(GeoMarker("mech_loc", GeoMarkerRole.PROVIDER_LIVE, it, mechanicName ?: "Mecánico Asignado"))
        }
        workshopPoint?.let {
            markers.add(GeoMarker("ws_loc", GeoMarkerRole.PROVIDER_WORKSHOP, it, workshopName ?: "Taller Mecánico"))
        }

        // Generate approach route if mechanic is live en-route to vehicle
        if (mechanicPoint != null && vehiclePoint != null) {
            val dist = calculateHaversineDistanceMeters(mechanicPoint, vehiclePoint)
            val etaSec = (dist / 8.33).roundToLong() // ~30 km/h mobile service speed
            routes.add(
                GeoRoute(
                    points = listOf(mechanicPoint, vehiclePoint),
                    distanceMeters = dist,
                    durationSeconds = etaSec,
                    routeColorHex = "#00E5FF"
                )
            )
        } else if (vehiclePoint != null && workshopPoint != null) {
            val dist = calculateHaversineDistanceMeters(vehiclePoint, workshopPoint)
            val etaSec = (dist / 11.1).roundToLong()
            routes.add(
                GeoRoute(
                    points = listOf(vehiclePoint, workshopPoint),
                    distanceMeters = dist,
                    durationSeconds = etaSec,
                    routeColorHex = "#2196F3"
                )
            )
        }

        val allPoints = markers.map { it.point } + routes.flatMap { it.points }
        val bounds = GeoBounds.fromPoints(allPoints)
        val cameraIntent = if (bounds != null && allPoints.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = routes,
            cameraIntent = cameraIntent,
        )
    }
}

object TowMapAdapter {
    fun buildMapState(
        vehicleOrigin: GeoPoint?,
        towTruckPoint: GeoPoint?,
        destinationPoint: GeoPoint?,
        driverName: String? = null,
    ): CommonMapState {
        val markers = mutableListOf<GeoMarker>()
        val routes = mutableListOf<GeoRoute>()

        vehicleOrigin?.let {
            markers.add(GeoMarker("tow_origin", GeoMarkerRole.VEHICLE_ORIGIN, it, "Vehículo Varado", isHighlighted = true))
        }
        towTruckPoint?.let {
            markers.add(GeoMarker("tow_truck", GeoMarkerRole.TOW_TRUCK, it, driverName ?: "Grúa en Camino"))
        }
        destinationPoint?.let {
            markers.add(GeoMarker("tow_dest", GeoMarkerRole.DESTINATION, it, "Destino de Remolque"))
        }

        if (towTruckPoint != null && vehicleOrigin != null) {
            val dist = calculateHaversineDistanceMeters(towTruckPoint, vehicleOrigin)
            routes.add(
                GeoRoute(
                    points = listOf(towTruckPoint, vehicleOrigin),
                    distanceMeters = dist,
                    durationSeconds = (dist / 8.33).roundToLong(),
                    routeColorHex = "#FF6D00"
                )
            )
        }
        if (vehicleOrigin != null && destinationPoint != null) {
            val dist = calculateHaversineDistanceMeters(vehicleOrigin, destinationPoint)
            routes.add(
                GeoRoute(
                    points = listOf(vehicleOrigin, destinationPoint),
                    distanceMeters = dist,
                    durationSeconds = (dist / 9.72).roundToLong(),
                    routeColorHex = "#00E676"
                )
            )
        }

        val allPoints = markers.map { it.point } + routes.flatMap { it.points }
        val bounds = GeoBounds.fromPoints(allPoints)
        val cameraIntent = if (bounds != null && allPoints.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = routes,
            cameraIntent = cameraIntent,
        )
    }
}

object PartsMapAdapter {
    fun buildMapState(
        storePoint: GeoPoint?,
        deliveryCourierPoint: GeoPoint?,
        customerDestination: GeoPoint?,
        storeName: String? = null,
    ): CommonMapState {
        val markers = mutableListOf<GeoMarker>()
        val routes = mutableListOf<GeoRoute>()

        storePoint?.let {
            markers.add(GeoMarker("part_store", GeoMarkerRole.STORE_LOCATION, it, storeName ?: "Repuestera"))
        }
        deliveryCourierPoint?.let {
            markers.add(GeoMarker("part_courier", GeoMarkerRole.PROVIDER_LIVE, it, "Repartidor"))
        }
        customerDestination?.let {
            markers.add(GeoMarker("part_dest", GeoMarkerRole.DESTINATION, it, "Destino de Entrega"))
        }

        if (deliveryCourierPoint != null && customerDestination != null) {
            val dist = calculateHaversineDistanceMeters(deliveryCourierPoint, customerDestination)
            routes.add(
                GeoRoute(
                    points = listOf(deliveryCourierPoint, customerDestination),
                    distanceMeters = dist,
                    durationSeconds = (dist / 11.1).roundToLong(),
                    routeColorHex = "#FF4081"
                )
            )
        } else if (storePoint != null && customerDestination != null) {
            val dist = calculateHaversineDistanceMeters(storePoint, customerDestination)
            routes.add(
                GeoRoute(
                    points = listOf(storePoint, customerDestination),
                    distanceMeters = dist,
                    durationSeconds = (dist / 11.1).roundToLong(),
                    routeColorHex = "#FF4081"
                )
            )
        }

        val allPoints = markers.map { it.point } + routes.flatMap { it.points }
        val bounds = GeoBounds.fromPoints(allPoints)
        val cameraIntent = if (bounds != null && allPoints.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = routes,
            cameraIntent = cameraIntent,
        )
    }
}

object UniversalServiceMapAdapter {
    fun buildMapState(
        clientPoint: GeoPoint?,
        providerPoint: GeoPoint?,
        serviceName: String,
        providerName: String? = null,
    ): CommonMapState {
        val markers = mutableListOf<GeoMarker>()
        val routes = mutableListOf<GeoRoute>()

        clientPoint?.let {
            markers.add(GeoMarker("client_loc", GeoMarkerRole.USER_LOCATION, it, "Cliente"))
        }
        providerPoint?.let {
            markers.add(GeoMarker("provider_loc", GeoMarkerRole.PROVIDER_LIVE, it, providerName ?: serviceName))
        }

        if (providerPoint != null && clientPoint != null) {
            val dist = calculateHaversineDistanceMeters(providerPoint, clientPoint)
            routes.add(
                GeoRoute(
                    points = listOf(providerPoint, clientPoint),
                    distanceMeters = dist,
                    durationSeconds = (dist / 10.0).roundToLong(),
                    routeColorHex = "#00E5FF"
                )
            )
        }

        val allPoints = markers.map { it.point } + routes.flatMap { it.points }
        val bounds = GeoBounds.fromPoints(allPoints)
        val cameraIntent = if (bounds != null && allPoints.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = routes,
            cameraIntent = cameraIntent,
        )
    }
}
