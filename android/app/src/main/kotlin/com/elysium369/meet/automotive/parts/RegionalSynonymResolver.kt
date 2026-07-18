package com.elysium369.meet.automotive.parts

data class ResolvedAlias(
    val partId: String,
    val system: AutomotiveSystem,
    val confidence: Double
)

class RegionalSynonymResolver(private val defaultLocale: String = "es-MX") {

    companion object {
        // Alias database mappings
        private val aliases = listOf(
            PartAlias("tijereta", "es-CR", "lower_control_arm"),
            PartAlias("tijera", "es-CR", "control_arm"),
            PartAlias("trapecio", "es", "control_arm"),
            PartAlias("brazo de control", "es", "control_arm"),
            PartAlias("brazo inferior", "es", "lower_control_arm"),
            PartAlias("horquilla", "es", "control_arm"),
            PartAlias("control arm", "en", "control_arm"),
            PartAlias("lower control arm", "en", "lower_control_arm"),
            PartAlias("wishbone", "en", "control_arm"),
            PartAlias("A-arm", "en", "control_arm"),

            PartAlias("muñón", "es-CR", "steering_knuckle"),
            PartAlias("mango", "es", "steering_knuckle"),
            PartAlias("porta masa", "es", "steering_knuckle"),
            PartAlias("knuckle", "en", "steering_knuckle"),

            PartAlias("punta de flecha", "es-CR", "cv_joint"),
            PartAlias("homocinética", "es", "cv_joint"),
            PartAlias("cv joint", "en", "cv_joint"),

            PartAlias("terminal", "es", "tie_rod_end"),
            PartAlias("terminal de dirección", "es", "tie_rod_end"),
            PartAlias("tie rod end", "en", "tie_rod_end"),

            PartAlias("bieleta", "es", "sway_bar_link"),
            PartAlias("link estabilizador", "es", "sway_bar_link"),
            PartAlias("barra estabilizadora link", "es", "sway_bar_link"),
            PartAlias("sway bar link", "en", "sway_bar_link")
        )
    }

    /**
     * Resolves a raw string input to its corresponding canonical part id and system category.
     * Checks for specific regional terms and filters out false positives like gear shift cables.
     */
    fun resolve(rawUserQuery: String, locale: String = defaultLocale): ResolvedAlias? {
        val queryLower = rawUserQuery.lowercase().trim()
        
        // Anti-hallucination check: If "tijereta" is queried with transmission context words,
        // it might refer to the selector cable. Otherwise, it is suspension.
        val hasTransmissionContext = queryLower.contains("cable") || 
                queryLower.contains("palanca") || 
                queryLower.contains("selector") || 
                queryLower.contains("caja") || 
                queryLower.contains("transmisión") || 
                queryLower.contains("shift")

        if (queryLower.contains("tijereta") && hasTransmissionContext) {
            // Refuse to resolve as control arm, or resolve as transmission cable
            return ResolvedAlias("shift_cable", AutomotiveSystem.TRANSMISSION, 0.9)
        }

        // Match against alias mappings
        for (alias in aliases) {
            if (queryLower.contains(alias.term)) {
                val system = when (alias.canonicalId) {
                    "lower_control_arm", "control_arm" -> AutomotiveSystem.SUSPENSION
                    "steering_knuckle" -> AutomotiveSystem.STEERING
                    "cv_joint" -> AutomotiveSystem.SUSPENSION
                    "tie_rod_end" -> AutomotiveSystem.STEERING
                    "sway_bar_link" -> AutomotiveSystem.SUSPENSION
                    else -> AutomotiveSystem.SUSPENSION
                }
                
                // Higher confidence if locale matches or if it is a general "es" or "en" alias
                val isLocaleMatch = alias.locale == locale || alias.locale == "es" || alias.locale == "en"
                val confidence = if (isLocaleMatch) 0.95 else 0.75
                
                return ResolvedAlias(alias.canonicalId, system, confidence)
            }
        }
        return null
    }
}
