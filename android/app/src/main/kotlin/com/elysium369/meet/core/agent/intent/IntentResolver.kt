package com.elysium369.meet.core.agent.intent

import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import com.elysium369.meet.core.agent.domain.AgentInput
import com.elysium369.meet.core.agent.domain.AgentIntent
import com.elysium369.meet.core.agent.domain.IntentResolution

interface IntentResolver {
    suspend fun resolve(input: AgentInput, context: AgentContextSnapshot): IntentResolution
}

/**
 * Deterministic and calibrated Intent & Entity Resolver (JEV Core).
 * Master Order Omega §15, §16, §26, §89.
 *
 * Enforces strict prompt injection guards:
 * "Ignore previous instructions", "refund payments", SQL/shell commands are treated
 * strictly as literal search strings, never synthesized into privileged capabilities.
 */
class DeterministicIntentResolver : IntentResolver {

    private val rideFromToRegex = Regex(
        """(?:p[ií]dame|solicitar|pedir|quiero|necesito)(?:\s+un)?\s+viaje\s+(?:de|desde)\s+(.+?)\s+(?:a|hacia|para)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val rideToRegex = Regex(
        """(?:llevarme|ll[eé]vame|ir|viajar|vamos)\s+(?:a|hacia)\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val emissionsRegex = Regex(
        """.*?\b(emisiones|catalizador|ox[ií]geno|o2|pre-?itv|dekra|humo|gases)\b.*?""",
        RegexOption.IGNORE_CASE
    )

    private val dtcDiagnosticsRegex = Regex(
        """.*?\b(diagn[oó]stic(?:o|ar)|escanear|fallas?|dtcs?|check engine|motor)\b.*?""",
        RegexOption.IGNORE_CASE
    )

    private val teachModeRegex = Regex(
        """.*?\b(ens[eé]ñame|modo ense[ñn]anza|c[oó]mo usar|aprender|gu[ií]a paso a paso)\b.*?""",
        RegexOption.IGNORE_CASE
    )

    private val promptInjectionKeywords = listOf(
        "ignore previous",
        "olvida las instrucciones",
        "refund",
        "reembols",
        "drop table",
        "system prompt",
        "grant admin",
        "sudo",
    )

    override suspend fun resolve(input: AgentInput, context: AgentContextSnapshot): IntentResolution {
        val trimmed = input.rawText.trim()
        if (trimmed.isEmpty()) {
            return IntentResolution(
                input = input,
                primaryIntent = null,
                requiresClarification = true,
                clarificationPrompt = "¿En qué puedo ayudarte hoy?",
                reasonCode = "EMPTY_INPUT",
            )
        }

        // Prompt Injection Defense (§89)
        val lower = trimmed.lowercase()
        val containsInjection = promptInjectionKeywords.any { lower.contains(it) }
        val sanitizedQuery = if (containsInjection) {
            // Strip hazardous instructions, treating rest purely as an inert search query
            trimmed.replace(Regex("(?i)ignore previous instructions.*|refund all my payments.*"), "").trim()
        } else {
            trimmed
        }

        // 1. Ride requests ("Pídame un viaje de mi casa a Multiplaza Escazú")
        rideFromToRegex.find(sanitizedQuery)?.let { match ->
            val originRaw = match.groupValues[1].trim()
            val destinationRaw = match.groupValues[2].trim()

            val originAlias = resolvePlaceAlias(originRaw)
            val entities = mutableMapOf<String, String>()
            if (originAlias != null) {
                entities["pickupAlias"] = originAlias
            } else {
                entities["pickupQuery"] = originRaw
            }
            entities["destinationQuery"] = destinationRaw

            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "ride.request",
                    targetDomain = "mobility",
                    targetCapabilityId = CapabilityId.of("ride.request"),
                    parameters = entities,
                    calibratedConfidence = 0.95,
                ),
                entities = entities,
                reasonCode = "RIDE_INTENT_RESOLVED",
            )
        }

        // Ride to destination only ("Llévame a Multiplaza Escazú")
        rideToRegex.find(sanitizedQuery)?.let { match ->
            val destinationRaw = match.groupValues[1].trim()
            val entities = mapOf(
                "pickupAlias" to "CURRENT_LOCATION",
                "destinationQuery" to destinationRaw,
            )

            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "ride.request",
                    targetDomain = "mobility",
                    targetCapabilityId = CapabilityId.of("ride.request"),
                    parameters = entities,
                    calibratedConfidence = 0.90,
                ),
                entities = entities,
                reasonCode = "RIDE_DESTINATION_RESOLVED",
            )
        }

        // 2. Emissions / Pre-ITV Specialist
        if (emissionsRegex.matches(sanitizedQuery)) {
            val entities = mapOf("query" to sanitizedQuery)
            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "emissions.analyze",
                    targetDomain = "emissions",
                    targetCapabilityId = CapabilityId.of("emissions.analyze_o2"),
                    parameters = entities,
                    calibratedConfidence = 0.92,
                ),
                entities = entities,
                reasonCode = "EMISSIONS_INTENT_RESOLVED",
            )
        }

        // 3. Diagnostics & DTCs (Master Mechanic)
        if (dtcDiagnosticsRegex.matches(sanitizedQuery)) {
            val entities = mapOf("query" to sanitizedQuery)
            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "vehicle.read_dtc",
                    targetDomain = "vehicle",
                    targetCapabilityId = CapabilityId.of("vehicle.read_dtc"),
                    parameters = entities,
                    calibratedConfidence = 0.90,
                ),
                entities = entities,
                reasonCode = "DIAGNOSTIC_INTENT_RESOLVED",
            )
        }

        // 4. Teach Mode / Living Guide
        if (teachModeRegex.matches(sanitizedQuery)) {
            val entities = mapOf("target" to "current_screen")
            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "guide.teach_mode",
                    targetDomain = "guide",
                    targetCapabilityId = CapabilityId.of("guide.start_tutorial"),
                    parameters = entities,
                    calibratedConfidence = 0.95,
                ),
                entities = entities,
                reasonCode = "TEACH_MODE_INTENT_RESOLVED",
            )
        }

        // Unresolved / Fallback
        return IntentResolution(
            input = input,
            primaryIntent = null,
            requiresClarification = true,
            clarificationPrompt = "No estoy seguro de qué acción deseas realizar. Puedes decirme: 'Pídame un viaje de mi casa a Multiplaza', 'Escanear vehículo' o 'Enséñame cómo usar esta pantalla'.",
            reasonCode = "UNCERTAIN_INTENT",
        )
    }

    private fun resolvePlaceAlias(raw: String): String? {
        val normalized = raw.lowercase().trim()
        return when {
            normalized in listOf("mi casa", "casa", "mi hogar", "hogar") -> "HOME"
            normalized in listOf("mi trabajo", "trabajo", "mi oficina", "oficina") -> "WORK"
            else -> null
        }
    }
}
