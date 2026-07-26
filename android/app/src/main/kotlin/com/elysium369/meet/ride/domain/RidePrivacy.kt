package com.elysium369.meet.ride.domain

enum class RideShareCategory {
    EXACT_LOCATION,
    BASIC_TELEMETRY,
    ACTIVE_DTCS,
    DTC_HISTORY,
    MAINTENANCE,
    INSTALLED_PARTS,
    CERTIFIED_REPORTS,
}

data class RideShareConsent(
    val tripId: String,
    val driverId: String,
    val category: RideShareCategory,
    val grantedAtEpochMs: Long?,
    val expiresAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null,
) {
    init {
        require(tripId.isNotBlank()) { "Trip ID is required" }
        require(driverId.isNotBlank()) { "Driver ID is required" }
        require(expiresAtEpochMs >= 0) { "Expiry cannot be negative" }
        require(grantedAtEpochMs == null || grantedAtEpochMs <= expiresAtEpochMs) {
            "Consent cannot start after it expires"
        }
    }

    fun canShare(nowEpochMs: Long, tripIsActive: Boolean): Boolean =
        tripIsActive &&
            grantedAtEpochMs != null &&
            revokedAtEpochMs == null &&
            nowEpochMs >= grantedAtEpochMs &&
            nowEpochMs <= expiresAtEpochMs
}

object RideConsentPolicy {
    val mechanicalCategories: Set<RideShareCategory> = setOf(
        RideShareCategory.BASIC_TELEMETRY,
        RideShareCategory.ACTIVE_DTCS,
        RideShareCategory.DTC_HISTORY,
        RideShareCategory.MAINTENANCE,
        RideShareCategory.INSTALLED_PARTS,
        RideShareCategory.CERTIFIED_REPORTS,
    )

    fun mechanicalDefaults(
        tripId: String,
        driverId: String,
        expiresAtEpochMs: Long,
    ): List<RideShareConsent> =
        mechanicalCategories.map { category ->
            RideShareConsent(
                tripId = tripId,
                driverId = driverId,
                category = category,
                grantedAtEpochMs = null,
                expiresAtEpochMs = expiresAtEpochMs,
            )
        }
}

enum class RideMechanicalSource {
    REAL_OBD,
    CERTIFIED_REPORT,
    VERIFIED_SERVICE_EVENT,
    DRIVER_STATEMENT,
}

enum class RideSampleFreshness {
    FRESH,
    STALE,
    CLOCK_SKEW,
}

data class RideMechanicalSample(
    val key: String,
    val displayValue: String,
    val source: RideMechanicalSource,
    val capturedAtEpochMs: Long,
) {
    init {
        require(key.isNotBlank()) { "Sample key is required" }
        require(displayValue.isNotBlank()) { "Sample value is required" }
        require(capturedAtEpochMs >= 0) { "Capture time cannot be negative" }
    }

    fun freshness(nowEpochMs: Long, maxAgeMs: Long): RideSampleFreshness {
        require(maxAgeMs >= 0) { "Maximum age cannot be negative" }
        val age = nowEpochMs - capturedAtEpochMs
        return when {
            age < 0 -> RideSampleFreshness.CLOCK_SKEW
            age <= maxAgeMs -> RideSampleFreshness.FRESH
            else -> RideSampleFreshness.STALE
        }
    }
}
