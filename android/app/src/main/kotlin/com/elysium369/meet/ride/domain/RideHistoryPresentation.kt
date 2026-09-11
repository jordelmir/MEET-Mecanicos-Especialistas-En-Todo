package com.elysium369.meet.ride.domain

import java.util.Locale

/** History distinguishes an offer from final authority and an estimate from captured distance. */
object RideHistoryPresentation {
    fun details(
        offeredMinor: Long,
        finalMinor: Long?,
        currency: String,
        status: String,
        serverState: String?,
        serverVersion: Long,
        estimatedDistanceKm: Double,
        locale: Locale,
    ): String {
        val fare = RideFinalFarePresentation.from(offeredMinor, finalMinor, currency, serverState, serverVersion)
        val amount = if (status == "COMPLETED" || serverState == "COMPLETED") {
            "Total: ${fare.total}"
        } else {
            "Tarifa ofrecida: ${fare.offered}"
        }
        val distance = if (estimatedDistanceKm.isFinite() && estimatedDistanceKm > 0) {
            "Distancia estimada: ${String.format(locale, "%.1f km", estimatedDistanceKm)}"
        } else {
            "Distancia no capturada"
        }
        return "$amount · $distance"
    }
}
