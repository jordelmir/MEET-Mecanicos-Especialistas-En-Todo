package com.elysium369.meet.ride.automatch

/**
 * Passenger-configurable auto-matching strategy for Elysium Vanguard Mobility.
 */
enum class RideAutoMatchStrategy(
    val id: String,
    val titleEs: String,
    val descriptionEs: String,
) {
    FASTEST_PICKUP(
        id = "FASTEST_PICKUP",
        titleEs = "Llegada más rápida",
        descriptionEs = "Asigna al conductor disponible con menor tiempo de llegada estimado (ETA).",
    ),
    LOWEST_FARE(
        id = "LOWEST_FARE",
        titleEs = "Mejor tarifa",
        descriptionEs = "Asigna la contraoferta más económica que cumpla con los requisitos mínimos.",
    ),
    HIGHEST_TRUST(
        id = "HIGHEST_TRUST",
        titleEs = "Mayor reputación",
        descriptionEs = "Prioriza conductores Vanguard / Elite con la mayor calificación Bayesiana demostrable.",
    ),
    BALANCED(
        id = "BALANCED",
        titleEs = "Recomendado (Balanceado)",
        descriptionEs = "Optimización combinada de tiempo de llegada, confianza y cercanía a tu oferta.",
    );

    companion object {
        fun fromId(id: String?): RideAutoMatchStrategy {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FASTEST_PICKUP
        }
    }
}
