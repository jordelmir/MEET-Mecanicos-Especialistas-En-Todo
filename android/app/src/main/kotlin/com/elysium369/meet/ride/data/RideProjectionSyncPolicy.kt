package com.elysium369.meet.ride.data

enum class RideProjectionConnectionState {
    IDLE,
    CONNECTING,
    LIVE,
    RECOVERING,
    AUTHENTICATION_REQUIRED,
}

object RideProjectionSyncPolicy {
    const val HEARTBEAT_INTERVAL_MS = 45_000L
    private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
    private const val MAX_RECONNECT_DELAY_MS = 30_000L

    fun reconnectDelayMs(attempt: Long): Long {
        val safeAttempt = attempt.coerceIn(0L, 30L).toInt()
        val multiplier = 1L shl safeAttempt
        return (INITIAL_RECONNECT_DELAY_MS * multiplier)
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }
}
