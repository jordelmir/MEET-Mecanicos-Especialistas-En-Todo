package com.elysium369.meet.presence

import kotlin.math.pow

/**
 * PresenceCore — Generalized authoritative location domain for Elysium.
 *
 * Truth Laws:
 * - SHARING_ENABLED != LOCATION_AVAILABLE
 * - LAST_KNOWN_LOCATION != CURRENT_LOCATION
 * - RECEIVED_LOCATION != FRESH_LOCATION
 * - LOW_ACCURACY != PRECISE_LOCATION
 * - OFFLINE != UNSAFE
 * - NO_RESPONSE != EMERGENCY
 * - POSSIBLE_INCIDENT != CONFIRMED_INCIDENT
 * - GEOFENCE_OBSERVATION != PHYSICAL_CERTAINTY
 * - GPS_COORDINATE != LEGAL_FACT
 * - CLOCK_UNTRUSTED never classifies observation as LIVE from client wall clock
 * - OLD OFFLINE SAMPLE != NEW CURRENT PRESENCE
 */

enum class PresenceState {
    INACTIVE,
    SHARING,
    PAUSED,
    STALE,
    REVOKED;

    val isActive: Boolean get() = this == SHARING
    val canShare: Boolean get() = this in listOf(INACTIVE, PAUSED, STALE)
}

enum class PresenceSource {
    GPS,
    NETWORK,
    FUSED,
    PASSIVE,
    MANUAL;

    val accuracyClass: AccuracyClass get() = when (this) {
        GPS, FUSED -> AccuracyClass.HIGH
        NETWORK -> AccuracyClass.MEDIUM
        PASSIVE -> AccuracyClass.LOW
        MANUAL -> AccuracyClass.UNKNOWN
    }
}

enum class AccuracyClass {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    val maxAcceptableMeters: Float get() = when (this) {
        HIGH -> 50f
        MEDIUM -> 200f
        LOW -> 1000f
        UNKNOWN -> Float.MAX_VALUE
    }
}

enum class ClockQuality {
    VERIFIED,
    UNTRUSTED_SKEW,
    SYNTHETIC_REJECTED
}

enum class LocationQuality {
    VERIFIED_HIGH_QUALITY,
    USABLE,
    LOW_ACCURACY,
    STALE,
    CLOCK_UNTRUSTED,
    SOURCE_UNTRUSTED,
    INVALID,
    UNKNOWN
}

enum class LocationFreshness {
    LIVE,
    RECENT,
    STALE,
    VERY_STALE,
    UNKNOWN
}

enum class SharingGranularity {
    PRECISE,
    APPROXIMATE,
    COARSE,
    CITY,
    PLACE_ONLY,
    STATE_ONLY;

    val isPrecise: Boolean get() = this == PRECISE
}

/**
 * Location data model for legacy / kernel compatibility.
 */
data class PresenceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val headingDegrees: Int? = null,
    val speedMetersPerSecond: Float? = null,
    val source: PresenceSource = PresenceSource.FUSED,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
        require(accuracyMeters >= 0f) { "Accuracy cannot be negative" }
        require(capturedAtEpochMs > 0) { "Timestamp required" }
    }

    fun isStale(nowEpochMs: Long, staleThresholdMs: Long = 300_000L): Boolean {
        return (nowEpochMs - capturedAtEpochMs) > staleThresholdMs
    }

    fun distanceTo(other: PresenceLocation): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = kotlin.math.sin(dLat / 2).pow(2) +
            Math.cos(Math.toRadians(latitude)) *
            Math.cos(Math.toRadians(other.latitude)) *
            kotlin.math.sin(dLon / 2).pow(2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMeters * c
    }
}

/**
 * Raw or processed sensor observation.
 * Monotonic stream key: (deviceId, streamId, sequence).
 */
data class PresenceSample(
    val sampleId: String,
    val principalId: String,
    val deviceId: String,
    val streamId: String,
    val sequence: Long,
    val capturedAt: Long,
    val receivedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val source: PresenceSource = PresenceSource.FUSED,
    val motionState: String? = null,
    val batteryState: Int? = null,
    val networkState: String? = null,
    val clockQuality: ClockQuality = ClockQuality.VERIFIED,
    val isBackfill: Boolean = false,
    val quality: LocationQuality = LocationQuality.USABLE,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude in -180.0..180.0) { "Longitude out of range" }
        require(accuracyMeters >= 0f) { "Accuracy cannot be negative" }
        require(sequence >= 0L) { "Sequence must be non-negative" }
    }

    val streamKey: String get() = "$deviceId:$streamId:$sequence"

    fun toLocation(): PresenceLocation = PresenceLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        altitudeMeters = altitude,
        headingDegrees = heading?.toInt(),
        speedMetersPerSecond = speed,
        source = source,
        capturedAtEpochMs = capturedAt,
    )

    fun deriveFreshness(nowEpochMs: Long, staleThresholdMs: Long = 300_000L): LocationFreshness {
        if (clockQuality == ClockQuality.UNTRUSTED_SKEW) return LocationFreshness.UNKNOWN
        val age = nowEpochMs - receivedAt
        return when {
            age < 30_000L -> LocationFreshness.LIVE
            age < staleThresholdMs -> LocationFreshness.RECENT
            age < staleThresholdMs * 3 -> LocationFreshness.STALE
            else -> LocationFreshness.VERY_STALE
        }
    }
}

/**
 * Multi-device publisher lease.
 * Only the device holding the active lease may update CURRENT PRESENCE snapshot.
 */
data class PresencePublisherLease(
    val principalId: String,
    val deviceId: String,
    val streamId: String,
    val grantedAt: Long,
    val expiresAt: Long,
    val priority: Int = 0,
) {
    fun isValid(nowEpochMs: Long): Boolean = nowEpochMs in grantedAt..expiresAt

    fun canPublish(sample: PresenceSample, nowEpochMs: Long): Boolean {
        if (!isValid(nowEpochMs)) return false
        return sample.principalId == principalId && sample.deviceId == deviceId
    }
}

data class PresenceGrant(
    val grantId: String,
    val ownerPrincipalId: String,
    val granteePrincipalId: String,
    val granularity: SharingGranularity,
    val startedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val purpose: String = "GENERAL",
    val isActive: Boolean = true,
) {
    fun isExpired(nowEpochMs: Long): Boolean {
        return expiresAtEpochMs?.let { it < nowEpochMs } ?: false
    }

    fun permits(granularity: SharingGranularity): Boolean {
        return this.granularity.ordinal <= granularity.ordinal
    }
}

sealed interface PresenceGrantValidation {
    data object ALLOWED : PresenceGrantValidation
    data class DENIED(val reason: String) : PresenceGrantValidation
}

object AntiStalkingPolicy {
    const val MAX_GRANT_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    val MIN_GRANULARITY_FOR_STRANGERS = SharingGranularity.CITY
    const val MAX_GRANTS_PER_PERSON = 20
    const val STALE_REMINDER_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

    fun validateGrant(
        owner: String,
        grantee: String,
        granularity: SharingGranularity,
        durationMs: Long,
        existingGrantCount: Int,
    ): PresenceGrantValidation {
        if (owner == grantee) return PresenceGrantValidation.DENIED("Cannot share with self")
        if (durationMs > MAX_GRANT_DURATION_MS) {
            return PresenceGrantValidation.DENIED("Maximum sharing duration is 24 hours")
        }
        if (existingGrantCount >= MAX_GRANTS_PER_PERSON) {
            return PresenceGrantValidation.DENIED("Maximum sharing grants reached")
        }
        return PresenceGrantValidation.ALLOWED
    }
}

/**
 * Authoritative latest snapshot of a principal's presence.
 */
data class PresenceSnapshot(
    val principalId: String,
    val state: PresenceState,
    val lastLocation: PresenceLocation? = null,
    val lastUpdatedAtEpochMs: Long = System.currentTimeMillis(),
    val activeGrants: List<PresenceGrant> = emptyList(),
    val sharingGranularity: SharingGranularity = SharingGranularity.PRECISE,
    val latestSample: PresenceSample? = null,
    val publisherDeviceId: String? = null,
    val freshness: LocationFreshness = LocationFreshness.UNKNOWN,
) {
    fun isStale(nowEpochMs: Long, staleThresholdMs: Long = 300_000L): Boolean {
        val loc = lastLocation ?: latestSample?.toLocation() ?: return true
        return loc.isStale(nowEpochMs, staleThresholdMs)
    }

    fun isSharedWith(granteePrincipalId: String): Boolean {
        return activeGrants.any { it.granteePrincipalId == granteePrincipalId && it.isActive }
    }

    /**
     * Applies a new sample under publisher lease and freshness doctrine.
     * Offline backfill does NOT rewind the snapshot marker if current snapshot is newer.
     */
    fun updateWithSample(
        sample: PresenceSample,
        lease: PresencePublisherLease?,
        nowEpochMs: Long,
    ): PresenceSnapshot {
        if (lease != null && !lease.canPublish(sample, nowEpochMs)) {
            return this
        }

        if (latestSample != null && sample.capturedAt < latestSample.capturedAt) {
            return this
        }

        val derivedFreshness = sample.deriveFreshness(nowEpochMs)
        val newState = if (derivedFreshness in listOf(LocationFreshness.LIVE, LocationFreshness.RECENT)) {
            PresenceState.SHARING
        } else {
            PresenceState.STALE
        }

        val loc = sample.toLocation()

        return copy(
            state = newState,
            lastLocation = loc,
            latestSample = sample,
            publisherDeviceId = sample.deviceId,
            lastUpdatedAtEpochMs = nowEpochMs,
            freshness = derivedFreshness,
        )
    }
}
