package com.elysium369.meet.ride.eta

/**
 * Verifiable origin provider for estimated arrival times (ETA).
 */
enum class RideEtaSource(
    val code: String,
    val labelEs: String,
    val isRealTimeTraffic: Boolean,
) {
    GOOGLE_TRAFFIC("GOOGLE_TRAFFIC", "Tráfico en tiempo real", true),
    ELYSIUM_HISTORICAL("ELYSIUM_HISTORICAL", "Modelo histórico Elysium", false),
    OPEN_ROUTING("OPEN_ROUTING", "Open Routing Service", false),
    HAVERSINE_FALLBACK("HAVERSINE_FALLBACK", "Estimado geométrico", false);

    companion object {
        fun fromCode(code: String?): RideEtaSource {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HAVERSINE_FALLBACK
        }
    }
}
