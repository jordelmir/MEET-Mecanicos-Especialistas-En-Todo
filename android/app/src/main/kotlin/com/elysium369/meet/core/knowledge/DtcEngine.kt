package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * DTC engine: normalization, lookup, and structured profile access.
 *
 * Normalization rules (per spec):
 *   "p0230"      -> P0230
 *   "P0230-13"   -> P0230 + failureType 0x13
 *   "P0230:13"   -> P0230 + failureType 0x13
 *   "u0100"      -> U0100
 *   "basura"     -> invalid
 *   "P9999"      -> invalid (second char must be 0-3)
 *
 * Code structure: ^[PBCU][0-3][0-9A-F]{3}([:-][0-9A-F]{2})?$
 */
class DtcEngine(
    private val profiles: Map<String, DtcProfile> = emptyMap()
) {
    sealed class NormalizeResult {
        data class Valid(
            val code: String,
            val failureTypeHex: String? = null
        ) : NormalizeResult()
        data class Invalid(val reason: String) : NormalizeResult()
    }

    fun normalize(raw: String): NormalizeResult {
        val trimmed = raw.trim().uppercase()
        if (trimmed.isEmpty()) return NormalizeResult.Invalid("empty")
        // Strip failure type suffix if present.
        val mainPart: String
        val failureType: String?
        val dashIdx = trimmed.indexOf('-').takeIf { it >= 0 } ?: trimmed.indexOf(':').takeIf { it >= 0 }
        if (dashIdx != null) {
            mainPart = trimmed.substring(0, dashIdx)
            failureType = trimmed.substring(dashIdx + 1)
            if (!HEX2.matches(failureType)) {
                return NormalizeResult.Invalid("malformed failure type: $failureType")
            }
        } else {
            mainPart = trimmed
            failureType = null
        }
        if (!DTC_REGEX.matches(mainPart)) {
            return NormalizeResult.Invalid("malformed DTC: $mainPart")
        }
        return NormalizeResult.Valid(mainPart, failureType)
    }

    fun isValid(raw: String): Boolean = normalize(raw) is NormalizeResult.Valid

    fun getDtcProfile(code: String): DtcProfile? {
        val norm = normalize(code)
        if (norm !is NormalizeResult.Valid) return null
        return profiles[norm.code]
    }

    fun getRelatedSystems(code: String): List<String> =
        getDtcProfile(code)?.relatedSystems.orEmpty()

    fun getLikelyComponents(code: String): List<String> =
        getDtcProfile(code)?.likelyComponents.orEmpty()

    fun getDiagnosticGraph(code: String): List<String> =
        getDtcProfile(code)?.diagnosticSteps.orEmpty()

    companion object {
        val DTC_REGEX = Regex("^[PBCU][0-3][0-9A-F]{3}$")
        private val HEX2 = Regex("^[0-9A-F]{2}$")
    }
}

/**
 * A DTC profile — what we know about a specific code.
 * Built from knowledge packs at import time, not hardcoded.
 */
@Serializable
data class RankedCauseDefinition(
    val id: String,
    val probability: Double
)

@Serializable
data class DtcProfile(
    val code: String,
    val system: String,
    val severity: String,
    val description: String,
    val relatedSystems: List<String> = emptyList(),
    val likelyComponents: List<String> = emptyList(),
    val rankedCauses: List<RankedCauseDefinition> = emptyList(),
    val diagnosticSteps: List<String> = emptyList(),
    val pidsToCheck: List<String> = emptyList(),
    val riskLevel: String = "MEDIUM",
    val sourceTier: String = "A_OWNED_CREATED"
)
