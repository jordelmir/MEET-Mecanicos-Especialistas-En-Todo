package com.elysium369.meet.mobility.domain.location

import com.elysium369.meet.mobility.domain.routing.GeoCoordinate
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class MobilityLocationFix(
    val coordinate: GeoCoordinate,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val elapsedRealtimeNanos: Long,
    val capturedAt: Instant,
)

sealed interface MobilityLocationEvent {
    data class Fix(
        val value: MobilityLocationFix,
    ) : MobilityLocationEvent

    data object PermissionMissing : MobilityLocationEvent

    data object ProviderUnavailable : MobilityLocationEvent
}

data class LocationTrackingPolicy(
    val intervalMillis: Long,
    val minimumDistanceMeters: Float,
    val highAccuracy: Boolean,
)

interface MobilityLocationTracker {
    fun observe(
        policy: LocationTrackingPolicy,
    ): Flow<MobilityLocationEvent>
}

object MobilityTrackingPolicies {
    val Idle = LocationTrackingPolicy(
        intervalMillis = 15_000L,
        minimumDistanceMeters = 30f,
        highAccuracy = false,
    )

    val Available = LocationTrackingPolicy(
        intervalMillis = 5_000L,
        minimumDistanceMeters = 15f,
        highAccuracy = true,
    )

    val EnRouteToPickup = LocationTrackingPolicy(
        intervalMillis = 2_000L,
        minimumDistanceMeters = 8f,
        highAccuracy = true,
    )

    val ActiveTrip = LocationTrackingPolicy(
        intervalMillis = 1_500L,
        minimumDistanceMeters = 5f,
        highAccuracy = true,
    )
}
