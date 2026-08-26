package com.elysium369.meet.ride.demand

/**
 * Demand level classification derived from H3 hexagonal cell supply/demand ratio.
 * Each level maps to a verifiable ratio of openRequests / availableDrivers.
 */
enum class RideDemandLevel {
    NORMAL,
    BUSY,
    HIGH,
    CRITICAL,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): RideDemandLevel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }

        /**
         * Deterministic classification from observable supply/demand metrics.
         * No opaque multiplier — the ratio is the evidence.
         */
        fun classify(openRequests: Int, availableDrivers: Int): RideDemandLevel {
            if (availableDrivers <= 0 && openRequests <= 0) return NORMAL
            if (availableDrivers <= 0 && openRequests > 0) return CRITICAL

            val ratio = openRequests.toDouble() / availableDrivers.toDouble()
            return when {
                ratio >= 5.0 -> CRITICAL
                ratio >= 3.0 -> HIGH
                ratio >= 1.5 -> BUSY
                else -> NORMAL
            }
        }
    }
}
