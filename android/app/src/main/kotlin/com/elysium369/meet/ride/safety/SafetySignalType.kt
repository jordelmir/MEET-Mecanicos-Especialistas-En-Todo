package com.elysium369.meet.ride.safety

/**
 * Guardian Mobility safety signal types.
 * Signals are OBSERVED or DERIVED — never declare criminal intent.
 */
enum class SafetySignalType {
    ROUTE_DEVIATION,
    UNEXPECTED_STOP,
    GPS_IMPOSSIBLE_JUMP,
    DRIVER_IDENTITY_MISMATCH,
    UNEXPECTED_TRIP_TERMINATION,
    EXTREME_SPEED,
    CRASH_ACCELERATION,
    SOS_TRIGGERED;

    companion object {
        fun fromString(value: String?): SafetySignalType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

enum class SafetySignalSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    companion object {
        fun fromString(value: String?): SafetySignalSeverity {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOW
        }
    }
}
