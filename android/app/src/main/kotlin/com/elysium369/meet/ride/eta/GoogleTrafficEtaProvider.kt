package com.elysium369.meet.ride.eta

import javax.inject.Inject

/**
 * Traffic-aware Google Routes ETA Provider wrapper.
 */
class GoogleTrafficEtaProvider @Inject constructor(
    private val fallbackProvider: FallbackEtaProvider,
) : RideEtaProvider {

    override suspend fun calculateEta(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): Result<RideEtaEstimate> {
        // Will connect to Google Routes API with session/traffic token when configured;
        // fails gracefully to FallbackEtaProvider without inventing data
        return fallbackProvider.calculateEta(originLat, originLon, destLat, destLon)
    }
}
