package com.elysium369.meet.safety.analytics.counternarcotics

import kotlinx.serialization.Serializable

/** Evidence pattern classifications never establish criminal guilt or identify suspects. */
@Serializable
enum class PatternTruthState(val publicLabel: String) {
    INSUFFICIENT_DATA("Datos insuficientes"),
    DOCUMENTED_PATTERN("Patrón documentado"),
    CORROBORATED_PATTERN("Patrón corroborado"),
    DISPUTED_PATTERN("Patrón disputado"),
}
