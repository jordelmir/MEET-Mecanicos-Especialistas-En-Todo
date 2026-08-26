package com.elysium369.meet.ride.reputation

/**
 * Closed taxonomy of passenger compliments. Emitted only after a verified COMPLETED trip.
 */
enum class RideCompliment(
    val code: String,
    val labelEs: String,
    val iconName: String,
) {
    COURTEOUS("COURTEOUS", "Trato Educado", "sentiment_satisfied"),
    SAFE_DRIVING("SAFE_DRIVING", "Conducción Segura", "shield"),
    FAST_PICKUP("FAST_PICKUP", "Llegó Rápido", "bolt"),
    CLEAN_VEHICLE("CLEAN_VEHICLE", "Vehículo Impecable", "cleaning_services"),
    GOOD_COMMUNICATION("GOOD_COMMUNICATION", "Buena Comunicación", "chat"),
    GOOD_NAVIGATION("GOOD_NAVIGATION", "Excelente Ruta", "navigation"),
    HELPFUL("HELPFUL", "Muy Servicial", "volunteer_activism"),
    PROFESSIONAL("PROFESSIONAL", "Profesionalismo", "verified");

    companion object {
        fun fromCode(code: String): RideCompliment? {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }
    }
}
