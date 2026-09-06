package com.elysium369.meet.mobility.domain.safety

import com.elysium369.meet.mobility.domain.guest.E164PhoneNumber
import com.elysium369.meet.mobility.domain.routing.GeoCoordinate
import java.time.Instant
import java.util.UUID

enum class EmergencySosType {
    SOS_BUTTON,
    ROUTE_DEVIATION,
    PROLONGED_STOP,
    COLLISION_DETECTED,
}

enum class EmergencySosState {
    TRIGGERED,
    DISPATCHED_POLICE,
    RESOLVED_FALSE_ALARM,
    RESOLVED_ASSISTED,
}

enum class RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class EmergencyContact(
    val contactId: UUID,
    val userId: UUID,
    val name: String,
    val phone: E164PhoneNumber,
    val notifyOnSos: Boolean = true,
    val notifyOnNightTrips: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Emergency contact name cannot be blank" }
    }
}

data class EmergencySosEvent(
    val eventId: UUID,
    val tripId: UUID,
    val triggeredBy: UUID,
    val eventType: EmergencySosType,
    val coordinate: GeoCoordinate,
    val speedMps: Float?,
    val state: EmergencySosState,
    val createdAt: Instant,
)

data class RouteDeviationRecord(
    val logId: UUID,
    val tripId: UUID,
    val distanceFromRouteMeters: Double,
    val thresholdMeters: Double = 500.0,
    val currentCoordinate: GeoCoordinate,
    val recordedAt: Instant,
) {
    val isAnomalous: Boolean
        get() = distanceFromRouteMeters > thresholdMeters

    val isSevereAnomaly: Boolean
        get() = distanceFromRouteMeters > 1500.0
}

