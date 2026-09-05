package com.elysium369.meet.presence

import kotlin.math.round

/**
 * PrivacyProjectionEngine — Transforms raw presence observations into privacy-filtered projections.
 *
 * Laws:
 * - Recipient must NEVER receive more precise data than authorized.
 * - Transform BEFORE recipient payload (server-side / gateway boundary).
 * - Anti-averaging: Do NOT use independent random noise around exact samples.
 *   Use stable/time-bounded coarse spatial bins to prevent triangulation by averaging.
 */

sealed interface ProjectedPresence {
    data object Hidden : ProjectedPresence

    data class SemanticPlaceOnly(
        val principalId: String,
        val placeName: String?,
        val capturedAt: Long,
        val freshness: LocationFreshness,
        val batteryPercentage: Int? = null,
    ) : ProjectedPresence

    data class Approximate(
        val principalId: String,
        val coarseLatitude: Double,
        val coarseLongitude: Double,
        val accuracyMeters: Float,
        val capturedAt: Long,
        val freshness: LocationFreshness,
        val batteryPercentage: Int? = null,
    ) : ProjectedPresence

    data class Precise(
        val principalId: String,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val altitude: Double? = null,
        val speed: Float? = null,
        val heading: Float? = null,
        val capturedAt: Long,
        val freshness: LocationFreshness,
        val motionState: String? = null,
        val batteryPercentage: Int? = null,
        val networkState: String? = null,
    ) : ProjectedPresence
}

object PrivacyProjectionEngine {
    // Discrete grid resolution for coarse projection: 0.01 degrees ~= 1.11 km
    private const val COARSE_GRID_STEP = 0.01
    private const val COARSE_ACCURACY_METERS = 1500f

    fun project(
        sample: PresenceSample,
        grant: LocationShareGrant,
        nowEpochMs: Long,
        currentPlaceName: String? = null,
    ): ProjectedPresence {
        if (!grant.isValid(nowEpochMs)) {
            return ProjectedPresence.Hidden
        }

        val freshness = sample.deriveFreshness(nowEpochMs)
        val battery = if (grant.shareBattery) sample.batteryState else null

        return when (grant.mode) {
            LocationShareMode.OFF -> ProjectedPresence.Hidden

            LocationShareMode.PLACE_ONLY -> ProjectedPresence.SemanticPlaceOnly(
                principalId = sample.principalId,
                placeName = currentPlaceName ?: "EN_TRANSITO",
                capturedAt = sample.capturedAt,
                freshness = freshness,
                batteryPercentage = battery,
            )

            LocationShareMode.APPROXIMATE -> {
                // Deterministic spatial binning (Anti-Averaging Law):
                // Repeated observations in the same 1km cell always map to the exact same cell centroid.
                val coarseLat = round(sample.latitude / COARSE_GRID_STEP) * COARSE_GRID_STEP
                val coarseLon = round(sample.longitude / COARSE_GRID_STEP) * COARSE_GRID_STEP

                ProjectedPresence.Approximate(
                    principalId = sample.principalId,
                    coarseLatitude = coarseLat,
                    coarseLongitude = coarseLon,
                    accuracyMeters = COARSE_ACCURACY_METERS,
                    capturedAt = sample.capturedAt,
                    freshness = freshness,
                    batteryPercentage = battery,
                )
            }

            LocationShareMode.PRECISE,
            LocationShareMode.JOURNEY_ONLY,
            LocationShareMode.TEMPORARY,
            LocationShareMode.EMERGENCY_ONLY -> {
                ProjectedPresence.Precise(
                    principalId = sample.principalId,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    accuracyMeters = sample.accuracyMeters,
                    altitude = sample.altitude,
                    speed = if (grant.shareMotion) sample.speed else null,
                    heading = if (grant.shareMotion) sample.heading else null,
                    capturedAt = sample.capturedAt,
                    freshness = freshness,
                    motionState = if (grant.shareMotion) sample.motionState else null,
                    batteryPercentage = battery,
                    networkState = if (grant.shareConnectivity) sample.networkState else null,
                )
            }
        }
    }
}
