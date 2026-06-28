package com.elysium369.meet.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VoiceFeedbackManager — Professional interactive voice assistant for OBD2 Elysium Vanguard.
 * Supports dynamic switching between Spanish and English depending on app settings.
 *
 * Utilizes native Android Text-to-Speech (TTS) for offline, low-latency voice notifications.
 * Implements Audio Focus Ducking to avoid pausing background media like YouTube/Spotify.
 */
@Singleton
class VoiceFeedbackManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var activeLanguage = "es"
    private val speechQueue = mutableListOf<Triple<String, String, Int>>()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

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
            
            // Register utterance progress listener to manage isSpeaking state dynamically
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    Log.d("VoiceFeedback", "TTS started speaking utteranceId=$utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonFocus()
                    Log.d("VoiceFeedback", "TTS finished speaking utteranceId=$utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    abandonFocus()
                    Log.e("VoiceFeedback", "TTS error speaking utteranceId=$utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    abandonFocus()
                    Log.e("VoiceFeedback", "TTS error speaking utteranceId=$utteranceId error=$errorCode")
                }
            })

            isInitialized = true
            Log.d("VoiceFeedback", "TTS Engine successfully initialized with activeLanguage=$activeLanguage.")

            // Flush queue
            synchronized(speechQueue) {
                for (item in speechQueue) {
                    speakInternal(item.first, item.second, item.third)
                }
                speechQueue.clear()
            }
        } else {
            Log.e("VoiceFeedback", "TextToSpeech onInit failed with status=$status")
        }
    }

    fun speak(es: String, en: String = es, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val isVoiceEnabled = prefs.getBoolean("voice_feedback_enabled", true)
        Log.d("VoiceFeedback", "speak() request. Enabled=$isVoiceEnabled. ES=\"$es\" EN=\"$en\"")
        if (!isVoiceEnabled) {
            Log.w("VoiceFeedback", "Voice feedback is disabled in preferences. Skipping speech.")
            return
        }

        if (isInitialized) {
            speakInternal(es, en, queueMode)
        } else {
            Log.w("VoiceFeedback", "TTS Engine is not yet initialized. Queuing speech: \"$es\"")
            synchronized(speechQueue) {
                speechQueue.add(Triple(es, en, queueMode))
            }
        }
    }

    private fun speakInternal(es: String, en: String, queueMode: Int) {
        try {
            val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("app_language", "es") ?: "es"
            
            if (lang != activeLanguage) {
                activeLanguage = lang
                updateEngineLanguage()
            }

            val textToSpeak = if (lang == "en") en else es
            Log.d("VoiceFeedback", "Speaking ($lang): \"$textToSpeak\"")

            // Request Audio Focus with DUCKING support before speaking
            requestFocus()

            tts?.speak(textToSpeak, queueMode, null, "ELYSIUM_TTS")
        } catch (e: Exception) {
            Log.e("VoiceFeedback", "Error during speak", e)
            abandonFocus()
        }
    }

    private fun requestFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            Log.d("VoiceFeedback", "Audio focus requested with DUCTING successfully.")
        } catch (e: Exception) {
            Log.w("VoiceFeedback", "Failed to request audio focus", e)
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
            Log.d("VoiceFeedback", "Audio focus abandoned successfully.")
        } catch (e: Exception) {
            Log.w("VoiceFeedback", "Failed to abandon audio focus", e)
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
        _isSpeaking.value = false
    }
}
