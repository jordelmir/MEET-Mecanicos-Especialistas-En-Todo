package com.elysium369.meet.ride.reputation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverBadges(
    @SerialName("identity_verified") val identityVerified: Boolean = false,
    @SerialName("license_verified") val licenseVerified: Boolean = false,
    @SerialName("vehicle_verified") val vehicleVerified: Boolean = false,
    @SerialName("liveness_verified") val livenessVerified: Boolean = false,
    @SerialName("liveness_checked_at") val livenessCheckedAt: String? = null,
    @SerialName("insurance_status") val insuranceStatus: String = "UNKNOWN",
    @SerialName("background_status") val backgroundStatus: String = "UNKNOWN",
)

@Serializable
data class DriverActiveVehicleSummary(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("make") val make: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("model_year") val modelYear: Int? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("plate_masked") val plateMasked: String? = null,
)

@Serializable
data class DriverPublicProfile(
    @SerialName("driver_id") val driverId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
    @SerialName("trust_tier") val trustTierRaw: String = "VERIFIED",
    @SerialName("bayesian_rating") val bayesianRating: Double? = null,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("total_trips") val totalTrips: Int = 0,
    @SerialName("active_since") val activeSince: String? = null,
    @SerialName("badges") val badges: DriverBadges = DriverBadges(),
    @SerialName("compliments") val compliments: Map<String, Int> = emptyMap(),
    @SerialName("active_vehicle") val activeVehicle: DriverActiveVehicleSummary? = null,
) {
    val trustTier: DriverTrustTier get() = DriverTrustTier.fromId(trustTierRaw)

    /**
     * Formatted truth string, e.g. "4.98 ★ · 2.952 viajes" or "Nuevo Conductor"
     */
    val formattedRatingSummary: String
        get() {
            return if (bayesianRating != null && totalTrips > 0) {
                String.format(java.util.Locale.US, "%.2f ★ · %,d viajes", bayesianRating, totalTrips)
            } else if (totalTrips > 0) {
                String.format(java.util.Locale.US, "%,d viajes verificados", totalTrips)
            } else {
                "Conductor Verificado"
            }
        }
}
