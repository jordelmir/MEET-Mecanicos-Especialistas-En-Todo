package com.elysium369.meet.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceFeedbackManager — Professional interactive voice assistant for MEET.
 * Supports dynamic switching between Spanish and English depending on app settings.
 *
 * Utilizes native Android Text-to-Speech (TTS) for offline, low-latency voice notifications.
 */
@Singleton
class VoiceFeedbackManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var activeLanguage = "es"
    private val speechQueue = mutableListOf<Pair<String, String>>()

    init {
        try {
            Log.d("VoiceFeedback", "Initializing Text-to-Speech engine...")
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("VoiceFeedback", "Failed to construct TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Read stored language initially
            val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            activeLanguage = prefs.getString("app_language", "es") ?: "es"
            
            updateEngineLanguage()
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.05f) // Slightly faster for responsive interface feedback
            isInitialized = true
            Log.d("VoiceFeedback", "TTS Engine successfully initialized with activeLanguage=$activeLanguage.")

            // Flush queue
            synchronized(speechQueue) {
                for (pair in speechQueue) {
                    speakInternal(pair.first, pair.second)
                }
                speechQueue.clear()
            }
        } else {
            Log.e("VoiceFeedback", "TextToSpeech onInit failed with status=$status")
        }
    }

    fun speak(es: String, en: String = es) {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val isVoiceEnabled = prefs.getBoolean("voice_feedback_enabled", true)
        android.util.Log.d("VoiceFeedback", "speak() request. Enabled=$isVoiceEnabled. ES=\"$es\" EN=\"$en\"")
        if (!isVoiceEnabled) {
            android.util.Log.w("VoiceFeedback", "Voice feedback is disabled in preferences. Skipping speech.")
            return
        }

        if (isInitialized) {
            speakInternal(es, en)
        } else {
            android.util.Log.w("VoiceFeedback", "TTS Engine is not yet initialized. Queuing speech: \"$es\"")
            synchronized(speechQueue) {
                speechQueue.add(Pair(es, en))
            }
        }
    }

    private fun speakInternal(es: String, en: String) {
        try {
            val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("app_language", "es") ?: "es"
            
            if (lang != activeLanguage) {
                activeLanguage = lang
                updateEngineLanguage()
            }

            val textToSpeak = if (lang == "en") en else es
            Log.d("VoiceFeedback", "Speaking ($lang): \"$textToSpeak\"")
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "MEET_TTS")
        } catch (e: Exception) {
            Log.e("VoiceFeedback", "Error during speak", e)
        }
    }

    private fun updateEngineLanguage() {
        val locale = if (activeLanguage == "en") Locale.US else Locale("es", "ES")
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w("VoiceFeedback", "Locale $locale not supported, falling back to default.")
            tts?.setLanguage(Locale.getDefault())
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
