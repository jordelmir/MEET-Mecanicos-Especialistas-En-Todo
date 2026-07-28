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

    fun reasonsFor(role: RideActorRole): List<RideCancellationReason> =
        when (role) {
            RideActorRole.PASSENGER -> listOf(
                RideCancellationReason.SAFETY_CONCERN,
                RideCancellationReason.VEHICLE_MISMATCH,
                RideCancellationReason.DRIVER_NO_SHOW,
                RideCancellationReason.EXCESSIVE_WAIT,
                RideCancellationReason.INCORRECT_PICKUP,
                RideCancellationReason.INCORRECT_DESTINATION,
                RideCancellationReason.CHANGE_OF_PLANS,
                RideCancellationReason.DUPLICATE_OR_ACCIDENTAL,
                RideCancellationReason.MEDICAL_EMERGENCY,
                RideCancellationReason.OTHER,
            )
            RideActorRole.DRIVER -> listOf(
                RideCancellationReason.SAFETY_CONCERN,
                RideCancellationReason.UNACCOMPANIED_MINOR,
                RideCancellationReason.CHILD_SEAT_REQUIRED,
                RideCancellationReason.TOO_MANY_PASSENGERS,
                RideCancellationReason.IDENTITY_MISMATCH,
                RideCancellationReason.HARASSMENT,
                RideCancellationReason.PROHIBITED_ITEM_OR_ACTIVITY,
                RideCancellationReason.DANGEROUS_PICKUP,
                RideCancellationReason.PASSENGER_NO_SHOW,
                RideCancellationReason.UNSAFE_VEHICLE_CONDITION,
                RideCancellationReason.MEDICAL_EMERGENCY,
                RideCancellationReason.OTHER,
            )
            RideActorRole.SYSTEM,
            RideActorRole.SAFETY_OPERATOR,
            -> listOf(RideCancellationReason.OTHER)
        }
}
