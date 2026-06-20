package com.elysium369.meet.core.copilot

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.elysium369.meet.core.alerts.AlertSeverity
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
    private val voiceFeedbackManager: VoiceFeedbackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val speechCooldowns = mutableMapOf<CopilotEventType, Long>()
    private var job: Job? = null

    // Configuration settings
    private val SPEECH_COOLDOWN_MS = 60_000L // 60s cooldown per event type

    fun startListeningToEvents() {
        if (job != null) return
        Log.i("SpeechService", "Starting MEET Copilot SpeechService event listener...")
        job = scope.launch {
            eventBus.events.collect { event ->
                processEventSpeech(event)
            }
        }
    }

    fun stopListeningToEvents() {
        job?.cancel()
        job = null
        Log.i("SpeechService", "Stopped MEET Copilot SpeechService event listener.")
    }

    private fun processEventSpeech(event: CopilotEvent) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        
        // 1. Check if voice copilot is globally enabled
        val isVoiceEnabled = prefs.getBoolean("voice_feedback_enabled", true)
        val isCopilotEnabled = prefs.getBoolean("voice_copilot_enabled", false)
        if (!isVoiceEnabled || !isCopilotEnabled) return

        // 2. Check Night Silence (Silencio Nocturno: 22:00 to 07:00)
        val isNightSilenceEnabled = prefs.getBoolean("copilot_night_silence", true)
        if (isNightSilenceEnabled && isNightTime()) {
            // Only speak critical level events during night silence, skip warning/info
            if (event.severity != AlertSeverity.CRITICAL) {
                Log.d("SpeechService", "Silencing event ${event.type} due to active Night Silence.")
                return
            }
        }

        // 3. Cooldown check per event type to prevent repetitive announcements
        val lastSpoken = speechCooldowns[event.type] ?: 0L
        if (now - lastSpoken < SPEECH_COOLDOWN_MS) {
            Log.d("SpeechService", "Skipping speech for ${event.type} due to cooldown.")
            return
        }

        // Update cooldown timestamp
        speechCooldowns[event.type] = now

        // 4. Dispatch speech request using TextToSpeech queue add mode for sequential announcements
        val queueMode = if (event.severity == AlertSeverity.CRITICAL) {
            TextToSpeech.QUEUE_FLUSH // Flush to announce safety issues immediately
        } else {
            TextToSpeech.QUEUE_ADD   // Queue sequentially
        }

        voiceFeedbackManager.speak(
            es = event.messageEs,
            en = event.messageEn,
            queueMode = queueMode
        )
    }

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 7
    }
}
