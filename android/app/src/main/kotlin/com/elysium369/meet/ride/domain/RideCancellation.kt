package com.elysium369.meet.ride.domain

enum class RideCancellationReason(
    val safetyRelated: Boolean,
) {
    SAFETY_CONCERN(true),
    UNACCOMPANIED_MINOR(true),
    CHILD_SEAT_REQUIRED(true),
    TOO_MANY_PASSENGERS(true),
    IDENTITY_MISMATCH(true),
    VEHICLE_MISMATCH(true),
    HARASSMENT(true),
    PROHIBITED_ITEM_OR_ACTIVITY(true),
    DANGEROUS_PICKUP(true),
    MEDICAL_EMERGENCY(true),
    UNSAFE_VEHICLE_CONDITION(true),
    PASSENGER_NO_SHOW(false),
    DRIVER_NO_SHOW(false),
    EXCESSIVE_WAIT(false),
    INCORRECT_PICKUP(false),
    INCORRECT_DESTINATION(false),
    CHANGE_OF_PLANS(false),
    DUPLICATE_OR_ACCIDENTAL(false),
    OTHER(false),
}

data class RideCancellationDecision(
    val requiresSafetyReview: Boolean,
    val automaticFeeAllowed: Boolean,
)

object RideCancellationPolicy {
    private const val MAX_DETAIL_LENGTH = 500

    fun evaluate(reason: RideCancellationReason): RideCancellationDecision =
        RideCancellationDecision(
            requiresSafetyReview = reason.safetyRelated,
            // Pilot policy: fees require a separately reviewed market policy.
            automaticFeeAllowed = false,
        )

    fun isDetailValid(reason: RideCancellationReason, detail: String?): Boolean {
        val normalized = detail?.trim().orEmpty()
        if (normalized.length > MAX_DETAIL_LENGTH) return false
        return reason != RideCancellationReason.OTHER || normalized.isNotEmpty()
    }
}
