package com.elysium369.meet.ride.communications

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class RideAuditoryCue {
    CALL_CONNECTED,
    CALL_ENDED,
    DRIVER_ARRIVED,
    PASSENGER_BOARDED,
    REROUTE_DETECTED,
    SAFETY_ALERT,
}

interface AuditoryFeedbackEngine {
    fun playCue(cue: RideAuditoryCue)
}

@Singleton
open class RideAuditoryFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceFeedbackManager: VoiceFeedbackManager? = null,
) : AuditoryFeedbackEngine {

    private val tag = "RideAuditoryFeedback"

    override fun playCue(cue: RideAuditoryCue) {
        when (cue) {
            RideAuditoryCue.CALL_CONNECTED -> {
                playTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                voiceFeedbackManager?.speak(
                    "Enlace de voz seguro activo.",
                    "Secure voice link active.",
                )
            }
            RideAuditoryCue.CALL_ENDED -> {
                playTone(ToneGenerator.TONE_PROP_PROMPT, 140)
            }
            RideAuditoryCue.DRIVER_ARRIVED -> {
                playTone(ToneGenerator.TONE_PROP_ACK, 220)
                voiceFeedbackManager?.speak(
                    "Llegada al punto de recogida confirmada. Esperando pasajero.",
                    "Arrival at pickup confirmed. Waiting for passenger.",
                )
            }
            RideAuditoryCue.PASSENGER_BOARDED -> {
                playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                voiceFeedbackManager?.speak(
                    "Pasajero a bordo verificado. Iniciando viaje hacia el destino.",
                    "Passenger onboard verified. Starting trip to destination.",
                )
            }
            RideAuditoryCue.REROUTE_DETECTED -> {
                playTone(ToneGenerator.TONE_PROP_BEEP, 100)
                voiceFeedbackManager?.speak(
                    "Recalculando ruta de navegación.",
                    "Recalculating navigation route.",
                )
            }
            RideAuditoryCue.SAFETY_ALERT -> {
                playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                voiceFeedbackManager?.speak(
                    "Alerta de seguridad activada.",
                    "Safety alert triggered.",
                )
            }
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGenerator.startTone(toneType, durationMs)
            toneGenerator.release()
        }.onFailure { error ->
            Log.w(tag, "Audio tone could not be played: ${error.message}")
        }
    }
}
