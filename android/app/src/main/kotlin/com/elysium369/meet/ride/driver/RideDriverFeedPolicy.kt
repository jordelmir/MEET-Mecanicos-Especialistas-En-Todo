package com.elysium369.meet.ride.driver

import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideDispatchExpiryPolicy

data class RideDriverFeedSnapshot(
    val eligibleRides: List<RideRequestEntity>,
    val ownPassengerRequests: List<RideRequestEntity>,
    val hiddenRides: List<RideRequestEntity>,
    val expiredCount: Int,
)

object RideDriverFeedPolicy {
    fun evaluate(
        rides: List<RideRequestEntity>,
        actorIds: Set<String>,
        activeRideId: String?,
        hiddenRideIds: Set<String>,
        nowEpochMs: Long,
    ): RideDriverFeedSnapshot {
        val eligible = mutableListOf<RideRequestEntity>()
        val own = mutableListOf<RideRequestEntity>()
        val hidden = mutableListOf<RideRequestEntity>()
        var expired = 0
        for (ride in rides) {
            if (ride.status != "OPEN" || ride.assignedDriverId != null || ride.requestId == activeRideId) continue
            if (!RideDispatchExpiryPolicy.remainsVisible(ride.createdAt, nowEpochMs)) {
                expired++
            } else if (ride.requestId in hiddenRideIds) {
                hidden += ride
            } else if (ride.passengerId in actorIds) {
                own += ride
            } else if (actorIds.isNotEmpty()) {
                eligible += ride
            }
        }
        return RideDriverFeedSnapshot(eligible, own, hidden, expired)
    }

    fun eligibleRides(
        rides: List<RideRequestEntity>, actorIds: Set<String>, activeRideId: String?,
        hiddenRideIds: Set<String>, nowEpochMs: Long,
    ): List<RideRequestEntity> = evaluate(rides, actorIds, activeRideId, hiddenRideIds, nowEpochMs).eligibleRides
}
