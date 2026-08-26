package com.elysium369.meet.ride.jurisdiction

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Immutable jurisdiction policy snapshot frozen at trip creation time.
 * Six months later, you can prove: "this trip was authorized under these exact rules."
 *
 * Costa Rica regulatory context: Expediente 23.736 remains in flux as of Aug 2026.
 * This engine treats regulation as versioned data, never as hardcoded if-statements.
 */
@Serializable
data class JurisdictionPolicy(
    @SerialName("id") val id: String,
    @SerialName("country_code") val countryCode: String = "CR",
    @SerialName("region_name") val regionName: String = "San Jose",
    @SerialName("policy_code") val policyCode: String,
    @SerialName("current_version") val currentVersion: Int = 1,
    @SerialName("legal_source") val legalSource: String,
)

/**
 * Frozen legal snapshot attached to each trip at creation time.
 * The trip cannot be retroactively judged by rules that didn't exist when it occurred.
 */
@Serializable
data class TripLegalSnapshot(
    @SerialName("trip_id") val tripId: String,
    @SerialName("jurisdiction_policy_id") val jurisdictionPolicyId: String,
    @SerialName("jurisdiction_policy_version") val jurisdictionPolicyVersion: Int,
    @SerialName("pricing_policy_version") val pricingPolicyVersion: Int,
    @SerialName("driver_eligibility_policy_version") val driverEligibilityPolicyVersion: Int,
) {
    /**
     * Human-readable provenance for dispute resolution and audit.
     */
    val auditSummary: String
        get() = "Viaje autorizado bajo política $jurisdictionPolicyId v$jurisdictionPolicyVersion " +
                "(tarifa v$pricingPolicyVersion, elegibilidad v$driverEligibilityPolicyVersion)"
}
