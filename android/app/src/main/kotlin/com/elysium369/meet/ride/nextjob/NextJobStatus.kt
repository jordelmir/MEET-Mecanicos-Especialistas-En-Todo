package com.elysium369.meet.ride.nextjob

enum class NextJobStatus {
    RESERVED,
    ACTIVATED,
    CANCELLED,
    DELAYED;

    companion object {
        fun fromString(value: String?): NextJobStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RESERVED
        }
    }
}
