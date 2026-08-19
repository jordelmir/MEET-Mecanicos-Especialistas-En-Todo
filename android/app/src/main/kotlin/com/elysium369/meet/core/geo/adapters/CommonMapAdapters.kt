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
        val route = if (rideState.route.size >= 2) {
            listOf(
                GeoRoute(
                    points = rideState.route.map {
                        GeoPoint(it.latitude, it.longitude, it.accuracyMeters, it.capturedAtEpochMs)
                    },
                    routeColorHex = "#00E5FF",
                )
            )
        } else emptyList()

        val bounds = GeoBounds.fromPoints(markers.map { it.point })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            routes = route,
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

        val bounds = GeoBounds.fromPoints(markers.map { it.point })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
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
        vehicleOrigin?.let {
            markers.add(GeoMarker("tow_origin", GeoMarkerRole.VEHICLE_ORIGIN, it, "Vehículo Varado", isHighlighted = true))
        }
        towTruckPoint?.let {
            markers.add(GeoMarker("tow_truck", GeoMarkerRole.TOW_TRUCK, it, driverName ?: "Grúa en Camino"))
        }
        destinationPoint?.let {
            markers.add(GeoMarker("tow_dest", GeoMarkerRole.DESTINATION, it, "Destino de Remolque"))
        }

        val bounds = GeoBounds.fromPoints(markers.map { it.point })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
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
        storePoint?.let {
            markers.add(GeoMarker("part_store", GeoMarkerRole.STORE_LOCATION, it, storeName ?: "Repuestera"))
        }
        deliveryCourierPoint?.let {
            markers.add(GeoMarker("part_courier", GeoMarkerRole.PROVIDER_LIVE, it, "Repartidor"))
        }
        customerDestination?.let {
            markers.add(GeoMarker("part_dest", GeoMarkerRole.DESTINATION, it, "Destino de Entrega"))
        }

        val bounds = GeoBounds.fromPoints(markers.map { it.point })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
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
        clientPoint?.let {
            markers.add(GeoMarker("client_loc", GeoMarkerRole.USER_LOCATION, it, "Cliente"))
        }
        providerPoint?.let {
            markers.add(GeoMarker("provider_loc", GeoMarkerRole.PROVIDER_LIVE, it, providerName ?: serviceName))
        }

        val bounds = GeoBounds.fromPoints(markers.map { it.point })
        val cameraIntent = if (bounds != null && markers.size > 1) {
            MapCameraIntent.FitBounds(bounds)
        } else if (markers.isNotEmpty()) {
            MapCameraIntent.CenterOn(markers.first().point)
        } else {
            MapCameraIntent.FollowUser
        }

        return CommonMapState(
            markers = markers,
            cameraIntent = cameraIntent,
        )
    }
}
