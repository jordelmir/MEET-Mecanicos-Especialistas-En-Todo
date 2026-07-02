package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Structured response from an external AI diagnostic service.
 * Per the spec, the AI must respond with:
 *   - summary
 *   - topCauses
 *   - nextTests
 *   - riskAssessment
 *   - explanation
 *   - contradictions
 *   - missingData
 *   - internetValidationQueries
 *   - safetyWarnings
 *   - confidence
 *   - partsLikelyNeeded
 *   - avoidReplacing
 *
 * The AI must say "no cambies X todavia" when evidence is missing.
 */
@Serializable
data class AiDiagnosticResponse(
    val summary: String,
    val topCauses: List<String>,
    val nextTests: List<String>,
    val riskAssessment: String,
    val explanation: String,
    val contradictions: List<String> = emptyList(),
    val missingData: List<String> = emptyList(),
    val internetValidationQueries: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val confidence: Double,           // 0..1
    val partsLikelyNeeded: List<String> = emptyList(),
    val avoidReplacing: List<String> = emptyList()
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be 0..1, got $confidence" }
    }

    /**
     * Format the response for the user. Includes a top-line summary,
     * the ranked causes, the next tests to run, and a "do not replace
     * yet" reminder when avoidReplacing is non-empty.
     */
    fun formatForUser(): String {
        val sb = StringBuilder()
        sb.appendLine("Resumen: $summary")
        sb.appendLine()
        if (topCauses.isNotEmpty()) {
            sb.appendLine("Causas principales:")
            topCauses.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        if (avoidReplacing.isNotEmpty()) {
            sb.appendLine("NO REEMPLACES TODAVIA:")
            avoidReplacing.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        if (nextTests.isNotEmpty()) {
            sb.appendLine("Siguientes pruebas:")
            nextTests.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        if (missingData.isNotEmpty()) {
            sb.appendLine("Datos faltantes:")
            missingData.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        if (safetyWarnings.isNotEmpty()) {
            sb.appendLine("Advertencias de seguridad:")
            safetyWarnings.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        sb.appendLine("Confianza: ${(confidence * 100).toInt()}%")
        return sb.toString()
    }
}
