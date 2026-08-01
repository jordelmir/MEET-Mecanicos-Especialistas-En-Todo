package com.elysium369.meet.ride.domain

data class RideDriverVehicleSummary(
    val id: String,
    val displayName: String,
    val make: String?,
    val model: String?,
    val modelYear: Int?,
    val color: String?,
    val plateMasked: String?,
    val fleetName: String?,
    val seats: Int,
    val verificationStatus: String,
    val active: Boolean,
)
