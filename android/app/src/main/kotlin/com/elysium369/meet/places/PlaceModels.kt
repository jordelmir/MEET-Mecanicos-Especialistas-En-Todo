package com.elysium369.meet.places

import com.elysium369.meet.presence.PresenceLocation
import com.elysium369.meet.presence.PresenceSample
import kotlin.math.pow

/**
 * Places Domain — Geofenced Places and Transition Evaluation with Hysteresis.
 *
 * Truth Laws:
 * - GEOFENCE_OBSERVATION != PHYSICAL_CERTAINTY
 * - Dual-boundary hysteresis: To enter: distance <= radius. To exit: distance > radius + hysteresis.
 * - Dwell time debounce: prevents boundary flapping from noisy GPS fixes.
 */

enum class PlaceCategory {
    HOME,
    WORK,
    SCHOOL,
    WORKSHOP,
    OTHER
}

enum class PlaceState {
    OUTSIDE,
    POSSIBLE_ENTRY,
    DWELLING,
    INSIDE,
    POSSIBLE_EXIT
}

enum class PlaceEvent {
    ENTERED,
    DWELLING,
    EXITED
}

data class PlaceRule(
    val ruleId: String,
    val eventType: PlaceEvent,
    val cooldownMs: Long = 300_000L,
)

data class Place(
    val placeId: String,
    val ownerPrincipalId: String = "",
    val principalId: String = ownerPrincipalId,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val category: PlaceCategory = PlaceCategory.OTHER,
    val rules: List<PlaceRule> = emptyList(),
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
        require(radiusMeters >= 50.0) { "Radius must be at least 50 meters for reliable geofencing" }
    }

    fun distanceTo(lat: Double, lon: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat - latitude)
        val dLon = Math.toRadians(lon - longitude)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
            Math.cos(Math.toRadians(latitude)) *
            Math.cos(Math.toRadians(lat)) *
            kotlin.math.sin(dLon / 2).pow(2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMeters * c
    }
}

data class PlaceObservation(
    val placeId: String,
    val principalId: String,
    val event: PlaceEvent,
    val location: PresenceLocation,
    val observedAtEpochMs: Long,
    val accuracyMeters: Float,
)

data class PlaceEventNotification(
    val placeId: String,
    val placeName: String,
    val eventType: PlaceEvent,
    val principalId: String,
    val principalName: String,
    val location: PresenceLocation,
    val timestampEpochMs: Long,
    val message: String,
)

object PlacePolicy {
    fun isInsideBoundary(location: PresenceLocation, place: Place): Boolean {
        return place.distanceTo(location.latitude, location.longitude) <= place.radiusMeters
    }

    fun shouldNotify(rule: PlaceRule, lastNotifiedMs: Long?, nowMs: Long): Boolean {
        if (lastNotifiedMs == null) return true
        return (nowMs - lastNotifiedMs) >= rule.cooldownMs
    }
}

data class PlaceObservationState(
    val placeId: String,
    val principalId: String,
    val currentState: PlaceState = PlaceState.OUTSIDE,
    val firstObservedInsideMs: Long? = null,
    val lastObservedAtMs: Long = 0L,
    val enterTransitionCount: Int = 0,
    val exitTransitionCount: Int = 0,
)

object PlaceEvaluationEngine {
    const val HYSTERESIS_METERS = 30.0 // Extra distance buffer needed to confirm an exit
    const val DWELL_THRESHOLD_MS = 60_000L // 1 minute of sustained presence inside radius required to emit INSIDE

    fun evaluate(
        place: Place,
        currentObservation: PlaceObservationState,
        sample: PresenceSample,
        nowEpochMs: Long,
    ): PlaceObservationState {
        val distance = place.distanceTo(sample.latitude, sample.longitude)

        return when (currentObservation.currentState) {
            PlaceState.OUTSIDE,
            PlaceState.POSSIBLE_ENTRY -> {
                if (distance <= place.radiusMeters) {
                    val firstInside = currentObservation.firstObservedInsideMs ?: nowEpochMs
                    val dwellDuration = nowEpochMs - firstInside
                    if (dwellDuration >= DWELL_THRESHOLD_MS) {
                        currentObservation.copy(
                            currentState = PlaceState.INSIDE,
                            firstObservedInsideMs = firstInside,
                            lastObservedAtMs = nowEpochMs,
                            enterTransitionCount = currentObservation.enterTransitionCount + 1,
                        )
                    } else {
                        currentObservation.copy(
                            currentState = PlaceState.DWELLING,
                            firstObservedInsideMs = firstInside,
                            lastObservedAtMs = nowEpochMs,
                        )
                    }
                } else {
                    currentObservation.copy(
                        currentState = PlaceState.OUTSIDE,
                        firstObservedInsideMs = null,
                        lastObservedAtMs = nowEpochMs,
                    )
                }
            }

            PlaceState.DWELLING -> {
                if (distance <= place.radiusMeters) {
                    val dwellDuration = nowEpochMs - (currentObservation.firstObservedInsideMs ?: nowEpochMs)
                    if (dwellDuration >= DWELL_THRESHOLD_MS) {
                        currentObservation.copy(
                            currentState = PlaceState.INSIDE,
                            lastObservedAtMs = nowEpochMs,
                            enterTransitionCount = currentObservation.enterTransitionCount + 1,
                        )
                    } else {
                        currentObservation.copy(lastObservedAtMs = nowEpochMs)
                    }
                } else {
                    currentObservation.copy(
                        currentState = PlaceState.OUTSIDE,
                        firstObservedInsideMs = null,
                        lastObservedAtMs = nowEpochMs,
                    )
                }
            }

            PlaceState.INSIDE,
            PlaceState.POSSIBLE_EXIT -> {
                if (distance > place.radiusMeters + HYSTERESIS_METERS) {
                    currentObservation.copy(
                        currentState = PlaceState.OUTSIDE,
                        firstObservedInsideMs = null,
                        lastObservedAtMs = nowEpochMs,
                        exitTransitionCount = currentObservation.exitTransitionCount + 1,
                    )
                } else {
                    currentObservation.copy(
                        currentState = PlaceState.INSIDE,
                        lastObservedAtMs = nowEpochMs,
                    )
                }
            }
        }
    }
}
