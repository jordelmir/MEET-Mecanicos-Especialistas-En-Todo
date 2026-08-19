package com.elysium369.meet.core.geo

/**
 * Universal geographic point representing a measured coordinate on Earth.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude is outside valid range: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude is outside valid range: $longitude" }
        require(accuracyMeters == null || accuracyMeters >= 0f) { "Accuracy cannot be negative" }
        require(capturedAtEpochMs >= 0) { "Captured time cannot be negative" }
    }
}

/**
 * Bounding box for map camera fitting.
 */
data class GeoBounds(
    val northLat: Double,
    val southLat: Double,
    val eastLng: Double,
    val westLng: Double,
) {
    init {
        require(northLat >= southLat) { "North lat must be >= south lat" }
    }

    companion object {
        fun fromPoints(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            var north = points[0].latitude
            var south = points[0].latitude
            var east = points[0].longitude
            var west = points[0].longitude
            for (p in points) {
                if (p.latitude > north) north = p.latitude
                if (p.latitude < south) south = p.latitude
                if (p.longitude > east) east = p.longitude
                if (p.longitude < west) west = p.longitude
            }
            return GeoBounds(north, south, east, west)
        }
    }
}

/**
 * Universal marker roles across all service verticals.
 */
enum class GeoMarkerRole {
    USER_LOCATION,
    VEHICLE_ORIGIN,
    DESTINATION,
    PROVIDER_LIVE,
    PROVIDER_WORKSHOP,
    TOW_TRUCK,
    STORE_LOCATION,
    INCIDENT_PIN,
    GENERIC_SERVICE,
}

/**
 * Visual map marker for the common map rendering engine.
 */
data class GeoMarker(
    val id: String,
    val role: GeoMarkerRole,
    val point: GeoPoint,
    val label: String,
    val subtitle: String? = null,
    val iconResName: String? = null,
    val isHighlighted: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Marker id cannot be blank" }
        require(label.isNotBlank()) { "Marker label cannot be blank" }
    }
}

/**
 * Geometric route connecting points.
 */
data class GeoRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Long? = null,
    val durationSeconds: Long? = null,
    val routeColorHex: String = "#00E5FF",
) {
    init {
        require(points.isEmpty() || points.size >= 2) { "Route must contain at least 2 points" }
    }
}

/**
 * Camera viewport intent.
 */
sealed interface MapCameraIntent {
    object FollowUser : MapCameraIntent
    data class CenterOn(val point: GeoPoint, val zoomLevel: Double = 15.0) : MapCameraIntent
    data class FitBounds(val bounds: GeoBounds, val paddingDp: Int = 48) : MapCameraIntent
}

/**
 * Universal map state consumable by CommonMapPanel and vertical adapters.
 */
data class CommonMapState(
    val markers: List<GeoMarker> = emptyList(),
    val routes: List<GeoRoute> = emptyList(),
    val cameraIntent: MapCameraIntent = MapCameraIntent.FollowUser,
    val isInteractive: Boolean = true,
    val showRecenterButton: Boolean = true,
    val showTrafficOverlay: Boolean = false,
) {
    init {
        require(markers.map { it.id }.toSet().size == markers.size) { "Marker IDs must be unique" }
    }

    fun marker(role: GeoMarkerRole): GeoMarker? = markers.firstOrNull { it.role == role }
}
