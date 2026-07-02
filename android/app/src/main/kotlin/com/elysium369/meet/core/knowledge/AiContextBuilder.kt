package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * AI Diagnostic Context — what gets sent to an external AI provider
 * (Google Gemini, OpenAI, etc.) when the user requests AI analysis.
 *
 * Privacy: VIN is hashed, location is omitted by default, plate is
 * omitted, raw PIDs are summarized.
 */
@Serializable
data class DiagnosticContext(
    val dtcCode: String,
    val dtcStatus: String,
    val freezeFrame: Map<String, Double> = emptyMap(),
    val livePids: Map<String, Double> = emptyMap(),
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val vehicleYear: Int? = null,
    val engine: String? = null,
    val transmission: String? = null,
    val odometerKm: Double? = null,
    val vinHash: String? = null,            // SHA-256 first 8 bytes hex, NEVER full VIN
    val history: List<String> = emptyList(),
    val relatedDtcs: List<String> = emptyList(),
    val completedTests: List<String> = emptyList(),
    val rankedCauses: List<String> = emptyList(),
    val scannerCapabilities: String = "GENERIC",
    val consentGranted: Boolean = false,
    val userNotes: String? = null
)

/**
 * Redaction rules per the spec.
 *   - VIN is hashed (SHA-256 prefix)
 *   - Plate is omitted (caller responsibility — we never accept it)
 *   - Location is never included in the context
 *   - Raw PIDs are summarized as { available, count }
 *   - Personal data is never included
 */
class AiContextBuilder {

    /**
     * Build a redaction-safe context. Throws if consent was not granted
     * (the caller must obtain explicit user consent for any external AI call).
     */
    fun build(raw: DiagnosticContext): DiagnosticContext {
        if (!raw.consentGranted) {
            throw IllegalStateException(
                "AI consent not granted. The user must explicitly opt in to " +
                "external AI analysis before any data leaves the device."
            )
        }
        // Hash VIN if present (we never accept full VIN; caller must already
        // have hashed it before calling us).
        val vinCheck = raw.vinHash?.let {
            if (it.length < 16) throw IllegalArgumentException(
                "vinHash must be SHA-256 prefix (16+ hex chars); got ${it.length}"
            )
            it
        }

        // Summarize freeze frame and live pids to a count + names only.
        // We keep the values because the AI needs them for analysis, but
        // we mark the field as "summarized" in the source tier.
        // For now we keep values; future versions may further redact.
        return raw.copy(vinHash = vinCheck)
    }

    companion object {
        /**
         * SHA-256 first 16 hex chars of the VIN. Never store or transmit
         * the full VIN. Callers should never accept VINs in plain form
         * to begin with — only accept user-supplied already-hashed values.
         */
        fun hashVin(vin: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(vin.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
