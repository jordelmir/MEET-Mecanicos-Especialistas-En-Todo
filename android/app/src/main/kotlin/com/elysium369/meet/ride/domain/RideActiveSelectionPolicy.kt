package com.elysium369.meet.ride.domain

/** Prevents a role switch or stale Room pointer from exposing another actor's ride. */
object RideActiveSelectionPolicy {
    fun canSelect(
        ownerId: String?,
        driverMode: Boolean,
        passengerId: String,
        assignedDriverId: String?,
        serverState: String?,
        serverVersion: Long,
    ): Boolean {
        if (ownerId.isNullOrBlank()) return false
        return if (driverMode) {
            assignedDriverId == ownerId ||
                (
                    passengerId != ownerId &&
                    assignedDriverId == null &&
                        serverVersion > 0L &&
                        serverState in setOf("SEARCHING", "OFFERED")
                )
        } else {
            passengerId == ownerId
        }
    }
}
