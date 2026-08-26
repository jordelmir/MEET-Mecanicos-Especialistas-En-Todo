package com.elysium369.meet.ride.demand

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-authoritative demand snapshot for a specific H3 resolution-8 cell.
 * Every field is observable evidence — no opaque multipliers.
 */
@Serializable
data class RideDemandSnapshot(
    @SerialName("found") val found: Boolean = false,
    @SerialName("demand_level") val demandLevelRaw: String = "NORMAL",
    @SerialName("available_drivers") val availableDrivers: Int = 0,
    @SerialName("open_requests") val openRequests: Int = 0,
    @SerialName("requests_per_driver") val requestsPerDriver: Double = 0.0,
    @SerialName("median_pickup_eta_seconds") val medianPickupEtaSeconds: Int? = null,
    @SerialName("notice_es") val noticeEs: String = "Demanda normal",
) {
    val demandLevel: RideDemandLevel get() = RideDemandLevel.fromString(demandLevelRaw)

    /**
     * Human-readable banner for passenger UI.
     * Displays only when demand is elevated and includes verifiable evidence.
     */
    val formattedBanner: String
        get() = when (demandLevel) {
            RideDemandLevel.CRITICAL -> "Demanda crítica · $availableDrivers conductores para $openRequests solicitudes"
            RideDemandLevel.HIGH -> "Alta demanda · $availableDrivers conductores disponibles cerca"
            RideDemandLevel.BUSY -> "Zona concurrida · ETA típico puede ser mayor"
            else -> ""
        }
}
