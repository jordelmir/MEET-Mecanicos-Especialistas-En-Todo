package com.elysium369.meet.core.agent.ui

/**
 * Sesión activa de dictado por voz sobre un campo de texto específico.
 * Controla el rango de texto propiedad de la voz ("speech-owned range")
 * para evitar duplicación de texto en resultados parciales de ASR.
 */
data class VoiceTextSession(
    val fieldId: AgentUiControlId,
    val baseValue: String,
    val lastPartialText: String = "",
    val fieldGeneration: Long = 0L,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
) {
    /**
     * Calcula el nuevo valor completo del campo sustituyendo el sufijo de voz previo
     * por el nuevo parcial de voz, preservando cualquier texto que ya estaba antes.
     */
    fun computeValueForPartial(newPartial: String): String {
        return if (baseValue.isBlank()) {
            newPartial
        } else {
            "$baseValue $newPartial".trim()
        }
    }
}
