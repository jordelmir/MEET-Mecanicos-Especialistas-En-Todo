package com.elysium369.meet.core.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ══════════════════════════════════════════════════════════════════════
 *  V O I C E   I N T E R A C T I O N   B U S
 *  ──────────────────────────────────────────────────────────────
 *  Canal único y centralizado para la distribución de eventos de voz.
 *  Garantiza un solo dueño del micrófono y permite a EVAIR, Laya
 *  y los overlays suscribirse de forma reactiva sin competir por
 *  SpeechRecognizer.
 * ══════════════════════════════════════════════════════════════════════
 */
class VoiceInteractionBus private constructor() {

    private val _transcripts = MutableSharedFlow<VoiceTranscriptEvent>(
        extraBufferCapacity = 64
    )
    val transcripts: SharedFlow<VoiceTranscriptEvent> = _transcripts.asSharedFlow()

    fun emit(event: VoiceTranscriptEvent) {
        _transcripts.tryEmit(event)
    }

    companion object {
        val default: VoiceInteractionBus by lazy { VoiceInteractionBus() }
    }
}
