package com.elysium369.meet.ride.realtime

/**
 * Broadcast channel abstraction for realtime events.
 */
class RideRealtimeCoordinator {
    companion object {
        fun passengerChannel(requestId: String) = "ride:passenger:$requestId"
        fun driverChannel(driverId: String) = "ride:driver:$driverId"
        fun tripChannel(tripId: String) = "ride:trip:$tripId"
    }

    enum class RideRealtimeEvent {
        REQUEST_PUBLISHED,
        DRIVER_EXPOSED,
        DRIVER_SEEN,
        OFFER_RECEIVED,
        OFFER_UPDATED,
        ASSIGNED,
        DRIVER_LOCATION,
        ETA_UPDATED,
        DRIVER_ARRIVED,
        BOARDING_VERIFIED,
        TRIP_STARTED,
        TRIP_COMPLETED
    }

    // Placeholder for future Broadcast subscription wiring
    // Initially this coexists with RideRemoteProjectionRepository's postgresChangeFlow
}
