package com.elysium369.meet.ride.dispatch

/**
 * Coordinator representing pure dispatch state observed from server events.
 * Android never decides dispatch.
 */
class RideDispatchCoordinator {

    /**
     * Pure representation of dispatch state.
     */
    data class DispatchState(
        val requestId: String,
        val currentWave: Int,
        val candidatesFound: Int,
        val candidatesEligible: Int,
        val seenCount: Int,
    )

    // Will be expanded when Broadcast channels are wired
}
