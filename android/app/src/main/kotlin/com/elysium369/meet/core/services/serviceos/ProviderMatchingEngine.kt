package com.elysium369.meet.core.services.serviceos

import kotlin.math.*

data class ProviderMatchScore(
    val providerId: String,
    val providerName: String,
    val totalScorePercent: Int,
    val isRecommended: Boolean,
    val positiveSignals: List<String>,
    val potentialWarnings: List<String>
)

object ProviderMatchingEngine {

    fun evaluateMatch(
        request: ServiceRequestV2,
        provider: ProviderOrganization
    ): ProviderMatchScore {
        var score = 50 // baseline
        val positive = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Vehicle Make Compatibility
        val vehicleMake = request.evidence.vehicleDisplayName.split(" ").firstOrNull()?.uppercase() ?: ""
        if (vehicleMake.isNotBlank()) {
            val specializesInMake = provider.supportedMakes.any { it.equals(vehicleMake, ignoreCase = true) }
            if (specializesInMake) {
                score += 20
                positive.add("Especialista documentado en vehículos $vehicleMake")
            } else if (provider.supportedMakes.isNotEmpty()) {
                warnings.add("Marca $vehicleMake no figura entre sus especialidades principales declaradas")
                score -= 10
            }
        }

        // 2. Verified Repairs Count
        if (provider.totalVerifiedRepairsCount >= 10) {
            score += 15
            positive.add("${provider.totalVerifiedRepairsCount} reparaciones verificadas con éxito en plataforma")
        } else if (provider.totalVerifiedRepairsCount > 0) {
            score += 5
            positive.add("${provider.totalVerifiedRepairsCount} caso(s) previo(s) completado(s)")
        }

        // 3. Distance & Modality
        if (provider.latitude != null && provider.longitude != null &&
            request.locationZone.latitude != null && request.locationZone.longitude != null
        ) {
            val distKm = calculateHaversineKm(
                request.locationZone.latitude, request.locationZone.longitude,
                provider.latitude, provider.longitude
            )
            if (distKm <= 5.0) {
                score += 10
                positive.add("Ubicación muy cercana (${String.format("%.1f", distKm)} km de distancia)")
            } else if (distKm > 25.0) {
                score -= 10
                warnings.add("Distancia considerable (${String.format("%.1f", distKm)} km)")
            }
        }

        // 4. Urgency & Emergency Mode
        if (request.urgency == ServiceRequestUrgency.URGENT_BREAKDOWN) {
            if (provider.isEmergencyModeActive) {
                score += 15
                positive.add("Taller en Modo Emergencia activo con respuesta inmediata")
            } else {
                warnings.add("No tiene modo emergencia activo; confirmar disponibilidad")
            }
        }

        // 5. On-Time Reliability
        if (provider.onTimeRatePercent >= 90) {
            score += 5
            positive.add("Alta puntualidad en entregas (${provider.onTimeRatePercent}%)")
        }

        val clampedScore = score.coerceIn(10, 99)
        val isRecommended = clampedScore >= 75 && warnings.isEmpty()

        return ProviderMatchScore(
            providerId = provider.id,
            providerName = provider.commercialName,
            totalScorePercent = clampedScore,
            isRecommended = isRecommended,
            positiveSignals = positive,
            potentialWarnings = warnings
        )
    }

    private fun calculateHaversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
