package com.elysium369.meet.ride.driver

import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideDispatchExpiryPolicy

object RideDriverFeedPolicy {

    fun eligibleRides(
        rides: List<RideRequestEntity>,
        actorIds: Set<String>,
        activeRideId: String?,
        hiddenRideIds: Set<String>,
        nowEpochMs: Long,
    ): List<RideRequestEntity> {
        if (actorIds.isEmpty()) {
            return emptyList()
        }

        return rides.filter { ride ->
            ride.status == "OPEN" &&
                ride.assignedDriverId == null &&
                ride.requestId != activeRideId &&
                ride.passengerId !in actorIds &&
                ride.requestId !in hiddenRideIds &&
                RideDispatchExpiryPolicy.remainsVisible(
                    ride.createdAt,
                    nowEpochMs,
                )
        }
    }
}
