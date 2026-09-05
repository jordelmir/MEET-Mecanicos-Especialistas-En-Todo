package com.elysium369.meet.safejourney

import com.elysium369.meet.presence.PresenceLocation

/**
 * SafeJourney — Continuous safety journey monitoring domain.
 *
 * Truth Laws:
 * - NO_RESPONSE != EMERGENCY
 * - OFFLINE != UNSAFE
 * - One GPS point inside radius != confirmed arrival (requires boundary, accuracy, dwell)
 * - SafeJourney state survives process death and reconciles properly.
 */

enum class SafeJourneyMode {
    WALKING,
    DRIVING,
    PUBLIC_TRANSIT,
    CYCLING
}

enum class SafeJourneyState {
    CREATED,
    ACTIVE,
    PROGRESSING,
    DELAYED,
    CHECK_REQUIRED,
    NO_RESPONSE,
    ARRIVED,
    CANCELLED,
    EXPIRED;

    val isInProgress: Boolean get() = this in listOf(ACTIVE, PROGRESSING, DELAYED, CHECK_REQUIRED, NO_RESPONSE)
    val isTerminal: Boolean get() = this in listOf(ARRIVED, CANCELLED, EXPIRED)
}

enum class JourneyState {
    PLANNED,
    ACTIVE,
    MISSED_CHECK_IN,
    EMERGENCY,
    COMPLETED,
    CANCELLED;

    val isActive: Boolean get() = this in listOf(ACTIVE, MISSED_CHECK_IN)
    val isTerminal: Boolean get() = this in listOf(COMPLETED, CANCELLED)
}

enum class CheckInStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}

data class CheckIn(
    val checkInId: String,
    val journeyId: String,
    val principalId: String,
    val status: CheckInStatus = CheckInStatus.CONFIRMED,
    val location: PresenceLocation? = null,
    val message: String? = null,
    val sentAtEpochMs: Long = System.currentTimeMillis(),
    val confirmedAtEpochMs: Long? = null,
    val confirmedByPrincipalId: String? = null,
    val isAutomatic: Boolean = false,
)

data class JourneyCheckIn(
    val checkInId: String,
    val journeyId: String,
    val principalId: String,
    val timestampMs: Long,
    val note: String? = null,
    val isSafe: Boolean = true,
)

enum class SafetyAlertType {
    CHECK_IN_MISSED,
    ROUTE_DEVIATION,
    SPEED_WARNING,
    STOP_PROLONGED,
    SOS_TRIGGERED;

    val isCritical: Boolean get() = this in listOf(SOS_TRIGGERED)
}

data class SafetyAlert(
    val alertId: String,
    val journeyId: String,
    val principalId: String,
    val type: SafetyAlertType,
    val message: String,
    val location: PresenceLocation? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val acknowledgedAtEpochMs: Long? = null,
    val acknowledgedByPrincipalId: String? = null,
)

data class SafeJourney(
    val journeyId: String,
    val principalId: String,
    val name: String = "",
    val origin: PresenceLocation? = null,
    val destination: PresenceLocation? = null,
    val destinationName: String? = null,
    val estimatedArrivalEpochMs: Long = 0L,
    val state: JourneyState = JourneyState.PLANNED,
    val journeyState: SafeJourneyState = SafeJourneyState.CREATED,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val sharedWithPrincipalIds: List<String> = emptyList(),
    val checkInIntervalMs: Long = 30 * 60 * 1000L,
    val startedAtEpochMs: Long? = null,
    val lastCheckInAtEpochMs: Long? = null,
    val lastKnownLocation: PresenceLocation? = null,
    val completedAtEpochMs: Long? = null,
    val publisherDeviceId: String = "",
    val originName: String = "",
    val destinationLat: Double = destination?.latitude ?: 0.0,
    val destinationLon: Double = destination?.longitude ?: 0.0,
    val destinationRadiusMeters: Double = 100.0,
    val mode: SafeJourneyMode = SafeJourneyMode.DRIVING,
    val expectedArrivalEpochMs: Long = if (estimatedArrivalEpochMs > 0L) estimatedArrivalEpochMs else (startedAtEpochMs ?: createdAtEpochMs) + 3600_000L,
    val lastProgressAtEpochMs: Long = startedAtEpochMs ?: createdAtEpochMs,
    val checkIns: List<JourneyCheckIn> = emptyList(),
) {
    fun isOverdue(nowEpochMs: Long): Boolean {
        val lastCheckIn = lastCheckInAtEpochMs ?: startedAtEpochMs ?: createdAtEpochMs
        return (nowEpochMs - lastCheckIn) > (checkInIntervalMs * 2)
    }

    /**
     * Invariant: A missed check-in or elapsed ETA transitions to NO_RESPONSE / CHECK_REQUIRED.
     * It strictly DOES NOT transition to EMERGENCY automatically.
     */
    fun evaluateTimers(nowEpochMs: Long): SafeJourney {
        if (journeyState.isTerminal) return this

        return when {
            nowEpochMs > expectedArrivalEpochMs + 15 * 60_000L -> {
                copy(
                    journeyState = SafeJourneyState.NO_RESPONSE,
                    state = JourneyState.MISSED_CHECK_IN,
                )
            }
            nowEpochMs > expectedArrivalEpochMs -> {
                copy(journeyState = SafeJourneyState.DELAYED)
            }
            else -> this
        }
    }

    fun recordCheckIn(checkIn: JourneyCheckIn): SafeJourney {
        return copy(
            checkIns = checkIns + checkIn,
            journeyState = if (checkIn.isSafe) SafeJourneyState.PROGRESSING else SafeJourneyState.CHECK_REQUIRED,
            lastProgressAtEpochMs = checkIn.timestampMs,
            lastCheckInAtEpochMs = checkIn.timestampMs,
        )
    }

    fun confirmArrival(nowEpochMs: Long): SafeJourney {
        return copy(
            journeyState = SafeJourneyState.ARRIVED,
            state = JourneyState.COMPLETED,
            completedAtEpochMs = nowEpochMs,
        )
    }
}
