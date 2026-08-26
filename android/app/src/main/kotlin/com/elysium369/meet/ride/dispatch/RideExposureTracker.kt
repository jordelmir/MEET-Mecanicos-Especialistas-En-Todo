package com.elysium369.meet.ride.dispatch

/**
 * Driver-side viewport tracker for ride requests.
 */
class RideExposureTracker {
    companion object {
        const val VISIBILITY_THRESHOLD_PERCENT = 50
        const val VISIBILITY_DURATION_MS = 500L
    }

    private val pendingVisibility = mutableMapOf<String, Long>()
    private val acknowledged = mutableSetOf<String>()

    @Synchronized
    fun onRequestVisible(requestId: String, nowMs: Long): Boolean {
        if (acknowledged.contains(requestId)) {
            return false
        }

        val firstVisible = pendingVisibility.getOrPut(requestId) { nowMs }
        val visibleDuration = nowMs - firstVisible

        return visibleDuration >= VISIBILITY_DURATION_MS
    }

    @Synchronized
    fun onRequestHidden(requestId: String) {
        pendingVisibility.remove(requestId)
    }

    @Synchronized
    fun markAcknowledged(requestId: String) {
        acknowledged.add(requestId)
        pendingVisibility.remove(requestId)
    }
}
