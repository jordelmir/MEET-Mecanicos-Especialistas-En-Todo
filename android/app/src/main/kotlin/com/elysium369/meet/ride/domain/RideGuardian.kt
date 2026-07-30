package com.elysium369.meet.ride.domain

enum class RideSafetySignalType {
    SOS,
    CHECK_IN_REQUEST,
    ROUTE_DEVIATION,
    LONG_STOP,
    POSSIBLE_COLLISION,
    SIGNAL_LOSS,
    VEHICLE_MISMATCH,
    PERSON_MISMATCH,
    HARASSMENT,
    MEDICAL_CONCERN,
}

object RideGuardianPolicy {
    val activeServerStates = setOf(
        "ASSIGNED",
        "DRIVER_EN_ROUTE",
        "ARRIVED",
        "PASSENGER_ONBOARD",
        "IN_PROGRESS",
    )

    fun canSignal(serverState: String?, serverVersion: Long): Boolean =
        serverVersion > 0L && serverState in activeServerStates

    fun severity(type: RideSafetySignalType): String = when (type) {
        RideSafetySignalType.SOS,
        RideSafetySignalType.POSSIBLE_COLLISION,
        RideSafetySignalType.MEDICAL_CONCERN,
        -> "CRITICAL"
        RideSafetySignalType.VEHICLE_MISMATCH,
        RideSafetySignalType.PERSON_MISMATCH,
        RideSafetySignalType.HARASSMENT,
        RideSafetySignalType.ROUTE_DEVIATION,
        -> "URGENT"
        RideSafetySignalType.CHECK_IN_REQUEST,
        RideSafetySignalType.LONG_STOP,
        RideSafetySignalType.SIGNAL_LOSS,
        -> "CHECK_IN"
    }
}
