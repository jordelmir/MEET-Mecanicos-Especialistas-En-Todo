package com.elysium369.meet.ride.dispatch

import com.elysium369.meet.ride.presence.RideDriverAvailability
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RideCandidate(
    @SerialName("driver_id") val driverId: String,
    @SerialName("vehicle_id") val vehicleId: String?,
    @SerialName("distance_meters") val distanceMeters: Int?,
    @SerialName("eta_seconds") val etaSeconds: Int?,
    @SerialName("availability") val availability: RideDriverAvailability,
    @SerialName("h3_r8") val h3R8: String?,
    @SerialName("speed_mps") val speedMps: Float?,
    @SerialName("heading") val heading: Int?,
    @SerialName("last_seen_at") val lastSeenAt: Long,
)
