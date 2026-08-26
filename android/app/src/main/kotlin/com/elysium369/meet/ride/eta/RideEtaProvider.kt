package com.elysium369.meet.ride.eta

interface RideEtaProvider {
    suspend fun calculateEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): Result<RideEtaEstimate>
}
