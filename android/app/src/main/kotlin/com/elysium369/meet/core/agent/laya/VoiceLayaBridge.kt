package com.elysium369.meet.core.agent.laya

/**
 * Result of hands-free vocal copilot query processing.
 */
data class VoiceCopilotResponse(
    val spokenAnswerEs: String,
    val spokenAnswerEn: String,
    val domain: String,
    val isEmergency: Boolean,
    val matchedIntent: String,
    val relevantDocumentSnippet: String? = null,
    val latencyMs: Long,
)

/**
 * VoiceLayaBridge — Connects speech recognition and vocal feedback with Laya AI
 * and the TinyEmbeddingIndex.
 *
 * Enables mechanics under a lift and drivers on the highway to speak natural questions
 * and receive instant, truthful, voice-synthesized answers completely offline.
 */
class VoiceLayaBridge(
    private val assistantEngine: LayaAssistantEngine = LayaAssistantEngine(),
    private val embeddingIndex: TinyEmbeddingIndex = TinyEmbeddingIndex(),
) {

    suspend fun processVoiceQuery(
        rawTranscript: String,
        rideContext: RideAssistantContext? = null,
        vehicleContext: VehicleAssistantContext? = null,
    ): VoiceCopilotResponse {
        val startEpoch = System.currentTimeMillis()
        val clean = rawTranscript.trim()

        // 1. Process via Laya Conversational Assistant
        val assistantResponse = assistantEngine.ask(
            query = clean,
            rideContext = rideContext,
            vehicleContext = vehicleContext,
        )

        // 2. Perform tiny semantic search for extra factual grounding if not an emergency
        val semanticResults = if (!assistantResponse.isEmergency && clean.length > 5) {
            embeddingIndex.search(clean, topK = 1)
        } else {
            emptyList()
        }

        val topDoc = semanticResults.firstOrNull()?.takeIf { it.similarityScore >= 0.40 }?.document

        // 3. Format concise voice-friendly response (avoiding robotic long paragraphs)
        val voiceTextEs = when {
            assistantResponse.isEmergency -> {
                "Protocolo de emergencia activado. Enlazando al 911 de Costa Rica y compartiendo tu ubicación satelital."
            }
            topDoc != null && assistantResponse.domain in listOf("automotive", "emissions", "legal_cr") -> {
                "${assistantResponse.text.substringBefore("\n\n")}. Según la referencia técnica: ${topDoc.content.take(160)}."
            }
            else -> {
                assistantResponse.text.substringBefore("\n\n")
            }
        }

        val voiceTextEn = when {
            assistantResponse.isEmergency -> "Emergency protocol activated. Connecting to 911 and sharing your location."
            else -> voiceTextEs // Fallback to localized Spanish for CR market
        }

        val elapsed = System.currentTimeMillis() - startEpoch

        return VoiceCopilotResponse(
            spokenAnswerEs = voiceTextEs,
            spokenAnswerEn = voiceTextEn,
            domain = assistantResponse.domain,
            isEmergency = assistantResponse.isEmergency,
            matchedIntent = assistantResponse.intent,
            relevantDocumentSnippet = topDoc?.title,
            latencyMs = elapsed,
        )
    }
}
