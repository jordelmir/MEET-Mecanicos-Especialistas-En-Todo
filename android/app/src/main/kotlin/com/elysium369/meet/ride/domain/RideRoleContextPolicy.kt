package com.elysium369.meet.ride.domain

/** Keeps passenger and driver navigation state independent under one account. */
object RideRoleContextPolicy {
    fun selectionOwnerKey(ownerId: String?, driverMode: Boolean): String? =
        ownerId?.takeIf { it.isNotBlank() }?.let {
            "$it#${if (driverMode) "RIDE_DRIVER" else "PASSENGER"}"
        }
}
