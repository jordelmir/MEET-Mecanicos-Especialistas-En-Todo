package com.elysium369.meet.core.domain

/**
 * MEET Vehicle Life OS — Claim Nature Classification.
 * Strictly separates observed telemetry facts from OEM authoritative data, mathematical derivations,
 * predictions, and human recommendations.
 */
enum class ClaimNature(val title: String, val badgeGlyph: String) {
    OBSERVED("Observado Directamente", "📡"),
    AUTHORITATIVE("Autoritativo OEM / Regulatorio", "🏛️"),
    DERIVED("Derivado Matemáticamente", "📐"),
    ESTIMATED("Estimado", "📊"),
    PREDICTED("Predicho por Modelo", "🔮"),
    RECOMMENDED("Recomendación Técnica", "💡"),
    USER_REPORTED("Reportado por Usuario", "👤")
}
