package com.elysium369.meet.ride.driver

data class NavigationGuidance(
    val maneuverType: String,
    val maneuverModifier: String?,
    val streetName: String?,
    val distanceToManeuverMeters: Long,
    val remainingDistanceMeters: Long,
    val remainingDurationSeconds: Long,
)
