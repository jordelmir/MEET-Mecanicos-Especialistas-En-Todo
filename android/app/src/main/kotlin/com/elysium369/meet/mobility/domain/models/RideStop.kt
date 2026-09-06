package com.elysium369.meet.mobility.domain.models

import java.util.UUID

enum class RideStopType {
    PICKUP,
    INTERMEDIATE,
    DESTINATION,
}

data class RideStop(
    val stopId: UUID,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val displayName: String?,
    val address: String?,
    val placeId: String?,
    val type: RideStopType,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be in [-90, 90], got $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be in [-180, 180], got $longitude" }
        if (accuracyMeters != null) {
            require(accuracyMeters >= 0f) { "Accuracy must be non-negative, got $accuracyMeters" }
        }
        require(sequence >= 0) { "Sequence must be non-negative, got $sequence" }
    }
}

data class RideStopInput(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val displayName: String?,
    val address: String?,
    val placeId: String?,
    val type: RideStopType,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be in [-90, 90], got $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be in [-180, 180], got $longitude" }
        if (accuracyMeters != null) {
            require(accuracyMeters >= 0f) { "Accuracy must be non-negative, got $accuracyMeters" }
        }
    }
}
