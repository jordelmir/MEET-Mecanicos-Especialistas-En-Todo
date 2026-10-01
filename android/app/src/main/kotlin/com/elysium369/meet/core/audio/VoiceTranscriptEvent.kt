package com.elysium369.meet.core.audio

/**
 * Eventos del flujo continuo de transcripción de voz (ASR).
 */
sealed interface VoiceTranscriptEvent {
    val utteranceId: String

    /**
     * Resultado parcial emitido en tiempo real mientras el humano continúa hablando.
     */
    data class Partial(
        override val utteranceId: String,
        val text: String,
    ) : VoiceTranscriptEvent

    /**
     * Resultado final consolidado emitido cuando el reconocedor detecta fin de elocución.
     */
    data class Final(
        override val utteranceId: String,
        val text: String,
        val confidence: Float? = null,
    ) : VoiceTranscriptEvent

    /**
     * Fallo o error en la captura de audio o servicio de reconocimiento.
     */
    data class Failure(
        override val utteranceId: String,
        val code: Int,
        val message: String? = null,
    ) : VoiceTranscriptEvent
}
