package com.elysium369.meet.ride.eta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RideEtaEstimate(
    @SerialName("eta_seconds") val etaSeconds: Int,
    @SerialName("distance_meters") val distanceMeters: Int,
    @SerialName("provider") val sourceRaw: String = "HAVERSINE_FALLBACK",
    @SerialName("confidence") val confidence: Double = 0.5,
    @SerialName("traffic_condition") val trafficCondition: String = "UNKNOWN",
    @SerialName("generated_at") val generatedAtMs: Long = System.currentTimeMillis(),
    @SerialName("expires_at") val expiresAtMs: Long = System.currentTimeMillis() + 60_000L,
) {
    val source: RideEtaSource get() = RideEtaSource.fromCode(sourceRaw)

    val etaMinutes: Int get() = ((etaSeconds + 59) / 60).coerceAtLeast(1)

    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMs

    /**
     * Formatted string showing estimated time and honest provenance, e.g. "~3 min (Tráfico en tiempo real)"
     */
    val formattedDisplay: String
        get() {
            val timeStr = if (etaMinutes <= 1) "~1 min" else "~$etaMinutes min"
            return "$timeStr (${source.labelEs})"
        }
}
