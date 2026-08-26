package com.elysium369.meet.ride.presence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RideDriverAvailability {
    @SerialName("OFFLINE") OFFLINE,
    @SerialName("AVAILABLE") AVAILABLE,
    @SerialName("OFFERING") OFFERING,
    @SerialName("RESERVED") RESERVED,
    @SerialName("FINISHING_CURRENT_TRIP") FINISHING_CURRENT_TRIP,
    @SerialName("EN_ROUTE_TO_PICKUP") EN_ROUTE_TO_PICKUP,
    @SerialName("PICKUP_WAITING") PICKUP_WAITING,
    @SerialName("IN_TRIP") IN_TRIP,
    @SerialName("PAUSED") PAUSED,
    @SerialName("SUSPENDED") SUSPENDED,
    @SerialName("STALE") STALE;

    val isDispatchable: Boolean get() = this in setOf(AVAILABLE, FINISHING_CURRENT_TRIP)
    val isActive: Boolean get() = this != OFFLINE && this != SUSPENDED && this != STALE
}

@Serializable
data class RideDriverPresenceState(
    @SerialName("driver_id") val driverId: String,
    @SerialName("vehicle_id") val vehicleId: String?,
    @SerialName("availability") val availability: RideDriverAvailability,
    @SerialName("latitude") val latitude: Double?,
    @SerialName("longitude") val longitude: Double?,
    @SerialName("heading") val heading: Int?,
    @SerialName("speed_mps") val speedMps: Float?,
    @SerialName("accuracy_m") val accuracyM: Float?,
    @SerialName("location_seq") val locationSeq: Long,
    @SerialName("current_trip_id") val currentTripId: String?,
    @SerialName("next_job_enabled") val nextJobEnabled: Boolean,
    @SerialName("last_seen_at") val lastSeenAt: Long,
)
