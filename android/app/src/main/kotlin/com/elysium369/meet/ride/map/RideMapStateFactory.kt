package com.elysium369.meet.ride.map

object RideMapStateFactory {
    fun create(
        passengerGps: RideGeoPoint? = null,
        pickup: RideGeoPoint? = null,
        stops: List<RideGeoPoint> = emptyList(),
        destination: RideGeoPoint? = null,
        driverGps: RideGeoPoint? = null,
        route: List<RideGeoPoint>? = null,
    ): RideMapState {
        val markers = buildList {
            passengerGps?.let {
                add(
                    RideMapMarker(
                        id = "passenger-live-gps",
                        role = RideMarkerRole.PASSENGER_GPS,
                        point = it,
                        label = "GPS exacto del pasajero",
                    ),
                )
            }
            pickup?.let {
                add(
                    RideMapMarker(
                        id = "requested-pickup",
                        role = RideMarkerRole.PICKUP,
                        point = it,
                        label = "Punto de recogida",
                    ),
                )
            }
            stops.forEachIndexed { index, point ->
                add(
                    RideMapMarker(
                        id = "requested-stop-${index + 1}",
                        role = RideMarkerRole.STOP,
                        point = point,
                        label = "Parada ${index + 1}",
                    ),
                )
            }
            destination?.let {
                add(
                    RideMapMarker(
                        id = "requested-destination",
                        role = RideMarkerRole.DESTINATION,
                        point = it,
                        label = "Destino",
                    ),
                )
            }
            driverGps?.let {
                add(
                    RideMapMarker(
                        id = "driver-live-gps",
                        role = RideMarkerRole.DRIVER,
                        point = it,
                        label = "Conductor",
                    ),
                )
            }
        }
        val resolvedRoute = when {
            route != null && route.size >= 2 -> route
            pickup != null && destination != null -> listOf(pickup) + stops + destination
            else -> emptyList()
        }
        return RideMapState(markers = markers, route = resolvedRoute)
    }
}
