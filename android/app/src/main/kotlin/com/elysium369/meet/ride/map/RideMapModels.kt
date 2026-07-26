package com.elysium369.meet.ride.map

enum class RideMarkerRole {
    PASSENGER_GPS,
    PICKUP,
    DESTINATION,
    DRIVER,
}

enum class RidePositionFreshness {
    FRESH,
    STALE,
    CLOCK_SKEW,
}

data class RideGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAtEpochMs: Long,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude is outside the valid range" }
        require(longitude in -180.0..180.0) { "Longitude is outside the valid range" }
        require(accuracyMeters == null || accuracyMeters >= 0f) {
            "Location accuracy cannot be negative"
        }
        require(capturedAtEpochMs >= 0) { "Capture time cannot be negative" }
    }

    fun freshness(nowEpochMs: Long, staleAfterMs: Long): RidePositionFreshness {
        require(staleAfterMs >= 0) { "Stale threshold cannot be negative" }
        val age = nowEpochMs - capturedAtEpochMs
        return when {
            age < 0 -> RidePositionFreshness.CLOCK_SKEW
            age <= staleAfterMs -> RidePositionFreshness.FRESH
            else -> RidePositionFreshness.STALE
        }
    }
}

data class RideMapMarker(
    val id: String,
    val role: RideMarkerRole,
    val point: RideGeoPoint,
    val label: String,
) {
    init {
        require(id.isNotBlank()) { "Marker ID is required" }
        require(label.isNotBlank()) { "Marker label is required" }
    }
}

data class RideMapState(
    val markers: List<RideMapMarker>,
    val route: List<RideGeoPoint> = emptyList(),
) {
    init {
        require(markers.map { it.id }.toSet().size == markers.size) {
            "Map marker IDs must be unique"
        }
        require(route.isEmpty() || route.size >= 2) {
            "A route requires at least two points"
        }
    }

    fun marker(role: RideMarkerRole): RideMapMarker? =
        markers.firstOrNull { it.role == role }
}
