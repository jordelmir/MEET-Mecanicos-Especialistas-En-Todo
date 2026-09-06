package com.elysium369.meet.mobility.domain.models

sealed interface RideEta {
    data class Routing(
        val durationSeconds: Long,
        val distanceMeters: Long,
        val provider: String = "OSRM",
    ) : RideEta

    data class HeuristicEstimate(
        val durationSeconds: Long,
        val distanceMeters: Long,
        val disclaimer: String = "Estimado geométrico sin tráfico en vivo",
    ) : RideEta

    data object Unavailable : RideEta
}
