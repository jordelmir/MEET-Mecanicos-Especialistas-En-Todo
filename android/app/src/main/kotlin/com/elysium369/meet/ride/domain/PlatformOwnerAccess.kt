package com.elysium369.meet.ride.domain

/**
 * Access is fail-closed: only a positive, server-authoritative decision may
 * expose the Trust Center. Email matching in the Android client is never an
 * authorization mechanism.
 */
enum class PlatformOwnerAccess {
    UNKNOWN,
    SIGNED_OUT,
    DENIED,
    UNAVAILABLE,
    GRANTED,
}

object PlatformOwnerAccessPolicy {
    fun canExposeTrustCenter(access: PlatformOwnerAccess): Boolean =
        access == PlatformOwnerAccess.GRANTED
}
