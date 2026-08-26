package com.elysium369.meet.ride.automatch

import com.elysium369.meet.ride.reputation.DriverTrustTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RideAutoMatchPolicy(
    @SerialName("request_id") val requestId: String,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("strategy") val strategyRaw: String = "FASTEST_PICKUP",
    @SerialName("max_fare_minor") val maxFareMinor: Long,
    @SerialName("minimum_trust_tier") val minimumTrustTierRaw: String = "VERIFIED",
    @SerialName("maximum_eta_seconds") val maximumEtaSeconds: Int = 600,
    @SerialName("allow_finishing_previous_trip") val allowFinishingPreviousTrip: Boolean = false,
) {
    val strategy: RideAutoMatchStrategy get() = RideAutoMatchStrategy.fromId(strategyRaw)
    val minimumTrustTier: DriverTrustTier get() = DriverTrustTier.fromId(minimumTrustTierRaw)
}
