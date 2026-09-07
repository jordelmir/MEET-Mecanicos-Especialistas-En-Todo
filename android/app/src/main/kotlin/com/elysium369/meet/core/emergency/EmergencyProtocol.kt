package com.elysium369.meet.core.emergency

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E M E R G E N C Y   P R O T O C O L
 *  ──────────────────────────────────────
 *  2am. Mountain road. Car dies. No signal. Alone.
 *
 *  ELYSIUM finds the nearest available mechanic, shows estimated
 *  arrival, price transparency, safety verification. Works with SMS
 *  when there's no internet. Works with Bluetooth mesh when there's
 *  no SMS.
 *
 *  THIS is what makes people download the APK.
 *
 *  Emergency tiers:
 *  ┌───────────────────────────────────────────────────────┐
 *  │  TIER 1: CRITICAL — life safety (roadside, medical)  │
 *  │  TIER 2: URGENT   — stranded, no transport           │
 *  │  TIER 3: STANDARD — breakdown, need help today       │
 *  │  TIER 4: SCHEDULED — can wait for appointment        │
 *  └───────────────────────────────────────────────────────┘
 * ══════════════════════════════════════════════════════════════════════
 */

enum class EmergencyTier(val maxResponseMinutes: Int, val label: String) {
    CRITICAL(15, "Emergencia crítica — peligro de vida"),
    URGENT(45, "Urgente — varado sin transporte"),
    STANDARD(120, "Estándar — necesito ayuda hoy"),
    SCHEDULED(1440, "Programable — puedo esperar"),
}

enum class EmergencyChannel {
    IN_APP,         // Full app experience
    SMS,            // SMS gateway (no internet)
    BLUETOOTH_MESH, // P2P mesh (no signal at all)
    VOICE_CALL,     // Phone call (last resort)
}

@Serializable
data class EmergencyRequest(
    val requestId: String,
    val userId: String,
    val tier: EmergencyTier,
    val description: String,
    val domain: UniversalServiceDomain,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationDescription: String = "",
    val channel: EmergencyChannel = EmergencyChannel.IN_APP,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val status: EmergencyStatus = EmergencyStatus.SEARCHING,
    val assignedResponderId: String? = null,
    val estimatedArrivalMinutes: Int? = null,
    val transparentPriceEstimate: String? = null,
)

enum class EmergencyStatus {
    SEARCHING,      // Looking for responders
    RESPONDER_FOUND,// Someone accepted
    EN_ROUTE,       // Responder on the way
    ON_SITE,        // Responder arrived
    IN_PROGRESS,    // Work being done
    COMPLETED,      // Emergency resolved
    CANCELLED,      // User cancelled
    EXPIRED,        // No responder found in time
}

@Serializable
data class EmergencyResponder(
    val userId: String,
    val displayName: String,
    val domain: UniversalServiceDomain,
    val isAvailable: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusKm: Double = 25.0,
    val trustScore: Double = 0.0,
    val verifiedIdentity: Boolean = false,
    val responseTimeMinutes: Int = 30,
    val baseRatePerHour: Double = 0.0,
)

@Serializable
data class EmergencyMatch(
    val responderId: String,
    val responderName: String,
    val distanceKm: Double,
    val estimatedArrivalMinutes: Int,
    val priceEstimate: PriceEstimate,
    val trustScore: Double,
    val isVerified: Boolean,
    val safetyScore: Double,
)

@Serializable
data class PriceEstimate(
    val minimumUsd: Double,
    val maximumUsd: Double,
    val baseRatePerHour: Double,
    val emergencySurcharge: Double,
    val platformFeePercent: Double,
    val disclosure: String,
) {
    /** INVARIANT: Emergency surcharge NEVER exceeds 50% of base */
    val isFairPriced: Boolean
        get() = emergencySurcharge <= baseRatePerHour * 0.5
}

// ─── Emergency Engine ───

class EmergencyEngine {

    companion object {
        /** Max emergency surcharge: 50% of base rate */
        const val MAX_EMERGENCY_SURCHARGE_PERCENT = 50.0
        /** Max platform fee even in emergencies: 5% */
        const val MAX_PLATFORM_FEE_PERCENT = 5.0
        /** Earth radius in km for distance calculation */
        const val EARTH_RADIUS_KM = 6371.0
    }

    private val responders = mutableListOf<EmergencyResponder>()
    private val activeEmergencies = mutableListOf<EmergencyRequest>()

    fun registerResponder(responder: EmergencyResponder) {
        responders.add(responder)
    }

    /**
     * Creates an emergency request and immediately searches for
     * the best available responder.
     */
    fun createEmergency(
        userId: String,
        tier: EmergencyTier,
        description: String,
        domain: UniversalServiceDomain,
        latitude: Double? = null,
        longitude: Double? = null,
        locationDescription: String = "",
        channel: EmergencyChannel = EmergencyChannel.IN_APP,
    ): EmergencyRequest {
        val request = EmergencyRequest(
            requestId = "emer-${System.currentTimeMillis()}",
            userId = userId,
            tier = tier,
            description = description,
            domain = domain,
            latitude = latitude,
            longitude = longitude,
            locationDescription = locationDescription,
            channel = channel,
        )
        activeEmergencies.add(request)
        return request
    }

    /**
     * Finds available responders sorted by:
     * 1. Distance (closest first)
     * 2. Trust score (highest first)
     * 3. Response time (fastest first)
     *
     * Safety filter: only verified responders for CRITICAL tier.
     */
    fun findResponders(
        request: EmergencyRequest,
    ): List<EmergencyMatch> {
        val available = responders.filter { r ->
            r.isAvailable &&
                r.domain == request.domain &&
                (request.tier != EmergencyTier.CRITICAL || r.verifiedIdentity)
        }

        return available.map { r ->
            val distance = if (request.latitude != null && request.longitude != null) {
                haversineDistance(
                    request.latitude, request.longitude,
                    r.latitude, r.longitude,
                )
            } else r.radiusKm // assume max if no GPS

            val arrivalMinutes = estimateArrival(distance, r.responseTimeMinutes)

            EmergencyMatch(
                responderId = r.userId,
                responderName = r.displayName,
                distanceKm = distance,
                estimatedArrivalMinutes = arrivalMinutes,
                priceEstimate = calculateEmergencyPrice(r, request.tier),
                trustScore = r.trustScore,
                isVerified = r.verifiedIdentity,
                safetyScore = computeSafetyScore(r),
            )
        }.sortedWith(
            compareBy<EmergencyMatch> { it.distanceKm }
                .thenByDescending { it.trustScore }
                .thenBy { it.estimatedArrivalMinutes }
        )
    }

    /**
     * Assigns a responder to an emergency.
     */
    fun assignResponder(requestId: String, responderId: String): Boolean {
        val idx = activeEmergencies.indexOfFirst { it.requestId == requestId }
        if (idx < 0) return false

        val match = findResponders(activeEmergencies[idx]).firstOrNull {
            it.responderId == responderId
        } ?: return false

        activeEmergencies[idx] = activeEmergencies[idx].copy(
            status = EmergencyStatus.RESPONDER_FOUND,
            assignedResponderId = responderId,
            estimatedArrivalMinutes = match.estimatedArrivalMinutes,
            transparentPriceEstimate = match.priceEstimate.disclosure,
        )
        return true
    }

    /**
     * Encodes emergency for SMS (when no internet).
     * Format: EMER|TIER|DOMAIN|LAT|LON|DESC (< 160 chars)
     */
    fun encodeForSms(request: EmergencyRequest): String {
        val lat = request.latitude?.let { String.format("%.4f", it) } ?: "NA"
        val lon = request.longitude?.let { String.format("%.4f", it) } ?: "NA"
        return "EMER|${request.tier.name.take(4)}|${request.domain.name.take(8)}|" +
            "$lat|$lon|${request.description.take(60)}"
    }

    /**
     * Decodes SMS emergency back to request.
     */
    fun decodeFromSms(sms: String, userId: String): EmergencyRequest? {
        val parts = sms.split("|")
        if (parts.size < 6 || parts[0] != "EMER") return null

        val tier = EmergencyTier.entries.firstOrNull {
            it.name.startsWith(parts[1])
        } ?: EmergencyTier.STANDARD

        val domain = try {
            UniversalServiceDomain.valueOf(parts[2])
        } catch (e: Exception) {
            UniversalServiceDomain.ROADSIDE
        }

        val lat = parts[3].toDoubleOrNull()
        val lon = parts[4].toDoubleOrNull()

        return createEmergency(
            userId, tier, parts[5], domain, lat, lon,
            channel = EmergencyChannel.SMS,
        )
    }

    // ─── Pricing ───

    private fun calculateEmergencyPrice(
        responder: EmergencyResponder,
        tier: EmergencyTier,
    ): PriceEstimate {
        val surchargeMultiplier = when (tier) {
            EmergencyTier.CRITICAL -> 0.50   // 50% surcharge max
            EmergencyTier.URGENT -> 0.25     // 25%
            EmergencyTier.STANDARD -> 0.10   // 10%
            EmergencyTier.SCHEDULED -> 0.0   // none
        }

        val surcharge = responder.baseRatePerHour * surchargeMultiplier
        val minPrice = responder.baseRatePerHour + surcharge
        val maxPrice = minPrice * 1.5 // 50% buffer for complexity

        return PriceEstimate(
            minimumUsd = minPrice,
            maximumUsd = maxPrice,
            baseRatePerHour = responder.baseRatePerHour,
            emergencySurcharge = surcharge,
            platformFeePercent = MAX_PLATFORM_FEE_PERCENT,
            disclosure = "Base: \$${String.format("%.0f", responder.baseRatePerHour)}/hr + " +
                "Urgencia: \$${String.format("%.0f", surcharge)} | " +
                "Rango: \$${String.format("%.0f", minPrice)}-\$${String.format("%.0f", maxPrice)} | " +
                "Comisión plataforma: ${MAX_PLATFORM_FEE_PERCENT.toInt()}% max",
        )
    }

    // ─── Distance & Safety ───

    fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    private fun estimateArrival(distanceKm: Double, baseMinutes: Int): Int {
        // Assume average 40 km/h in emergency conditions
        val travelMinutes = (distanceKm / 40.0 * 60).toInt()
        return baseMinutes + travelMinutes
    }

    private fun computeSafetyScore(responder: EmergencyResponder): Double {
        var score = 0.0
        if (responder.verifiedIdentity) score += 0.5
        score += (responder.trustScore * 0.5).coerceAtMost(0.5)
        return score.coerceIn(0.0, 1.0)
    }

    val activeCount: Int get() = activeEmergencies.count {
        it.status !in listOf(EmergencyStatus.COMPLETED, EmergencyStatus.CANCELLED, EmergencyStatus.EXPIRED)
    }

    val registeredResponders: Int get() = responders.size
}
