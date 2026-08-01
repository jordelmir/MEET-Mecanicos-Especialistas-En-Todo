package com.elysium369.meet.ride.domain

import java.time.Instant
import java.time.ZoneId

object RideDriverPresencePolicy {
    const val MAX_SESSION_MS = 12 * 60 * 60 * 1000L

    fun requiresChallenge(
        lastVerifiedAtEpochMs: Long?,
        nowEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val verifiedAt = lastVerifiedAtEpochMs ?: return true
        if (nowEpochMs - verifiedAt !in 0 until MAX_SESSION_MS) return true
        val verifiedDay = Instant.ofEpochMilli(verifiedAt).atZone(zoneId).toLocalDate()
        val currentDay = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        return verifiedDay != currentDay
    }
}
