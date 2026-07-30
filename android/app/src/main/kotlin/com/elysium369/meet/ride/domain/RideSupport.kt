package com.elysium369.meet.ride.domain

enum class RideSupportCategory {
    LOST_ITEM,
    WRONG_CHARGE,
    WRONG_DRIVER,
    WRONG_PASSENGER,
    ROUTE_ISSUE,
    ACCIDENT,
    CANCELLATION,
    PAYMENT,
    COMMISSION,
    DOCUMENT,
    BEHAVIOR,
    OTHER,
}

object RideSupportPolicy {
    const val MIN_SUMMARY_LENGTH = 10
    const val MAX_SUMMARY_LENGTH = 1_000

    fun isValidSummary(value: String): Boolean =
        value.trim().length in MIN_SUMMARY_LENGTH..MAX_SUMMARY_LENGTH
}
