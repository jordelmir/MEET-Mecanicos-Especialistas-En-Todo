package com.elysium369.meet.core.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceCommand {
    CYCLE_GAUGES,
    NAVIGATE_DASHBOARD,
    NAVIGATE_DIAGNOSTICS,
    NAVIGATE_OSCILLOSCOPE,
    NAVIGATE_LOCATOR,
    NAVIGATE_SETTINGS,
    SAY_TEMPERATURE,
    SAY_VOLTAGE,
    SAY_RPM,
    CLEAR_DTCS,
    DISABLE_ALERTS,
    ENABLE_ALERTS,
    DEACTIVATE_COPILOT,
    
    // New status commands for 100% app info coverage
    SAY_DIAGNOSTICS,
    SAY_FUEL_ECONOMY,
    SAY_VEHICLE_DNA,
    SAY_PERITO_REPORT,
    SAY_GENERAL_STATUS
}

@Singleton
class VoiceCommandManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceFeedbackManager: VoiceFeedbackManager,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isRecognizerActive = false

    private val _isListeningState = MutableStateFlow(false)
    val isListeningState: StateFlow<Boolean> = _isListeningState.asStateFlow()

    // 10-second conversation window state
    private var lastTriggerTime: Long = 0L
    private val CONVERSATION_WINDOW_MS = 10000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    init {
        // Observe speaking state of TTS to pause listening and avoid feedback loop
        scope.launch {
            voiceFeedbackManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    Log.d("VoiceCommand", "TTS active, pausing speech recognizer.")
                    cancelListeningInternal()
                } else {
                    val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                    val isCopilotEnabled = prefs.getBoolean("voice_copilot_enabled", false)
                    if (isListening && isCopilotEnabled) {
                        mainHandler.postDelayed({
                            if (isListening && !voiceFeedbackManager.isSpeaking.value) {
                                startListeningInternal()
                            }
                        }, 800) // Small delay to let audio channel settle
                    }
                }
            }
        }
    }

    fun startCopilot() {
        Log.i("VoiceCommand", "Starting Voice Copilot system...")
        isListening = true
        _isListeningState.value = true
        mainHandler.post {
            startListeningInternal()
        }
    }

    fun stopCopilot() {
        Log.i("VoiceCommand", "Stopping Voice Copilot system...")
        isListening = false
        _isListeningState.value = false
        mainHandler.post {
            cancelListeningInternal()
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceCommand", "Error destroying SpeechRecognizer", e)
        }
        speechRecognizer = null
        isRecognizerActive = false
    }

    fun destroy() {
        scope.cancel() // Cancel Hilt coroutine scope
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            destroyRecognizer()
        }
    }

    private fun startListeningInternal() {
        if (!isListening || voiceFeedbackManager.isSpeaking.value) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceCommandManager)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

            val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val lang = prefs.getString("app_language", "es") ?: "es"
            val localeStr = if (lang == "en") "en-US" else "es-ES"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeStr)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable partial results for early wake-word detection
        }

        try {
            speechRecognizer?.startListening(intent)
            isRecognizerActive = true
            Log.d("VoiceCommand", "SpeechRecognizer started listening...")
        } catch (e: Exception) {
            Log.e("VoiceCommand", "Failed to start listening", e)
            isRecognizerActive = false
        }
    }

    private fun cancelListeningInternal() {
        try {
            speechRecognizer?.cancel()
            isRecognizerActive = false
            Log.d("VoiceCommand", "SpeechRecognizer cancelled successfully.")
        } catch (e: Exception) {
            Log.e("VoiceCommand", "Failed to cancel SpeechRecognizer", e)
        }
    }

    private fun speakConfirmation(es: String, en: String) {
        voiceFeedbackManager.speak(es, en)
    }

    private fun playBeepSound() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            toneG.release()
        } catch (e: Exception) {
            Log.w("VoiceCommand", "Failed to play trigger beep", e)
        }
    }

    private fun processSpokenText(text: String) {
        val cleanText = text.lowercase().trim()
        Log.d("VoiceCommand", "Processing voice input: \"$cleanText\"")

        val now = System.currentTimeMillis()
        val hasWakeWord = cleanText.contains("elysium") || cleanText.contains("elísium")
        val inWindow = now - lastTriggerTime < CONVERSATION_WINDOW_MS

        if (!hasWakeWord && !inWindow) {
            Log.d("VoiceCommand", "Wake word 'Elysium' not found and conversation window expired. Ignoring input.")
            return
        }

        // Determine the command phrase to analyze
        val phraseToProcess = if (hasWakeWord) {
            lastTriggerTime = now // Reset conversation window
            // Extract everything after wake word
            val splitWord = if (cleanText.contains("elísium")) "elísium" else "elysium"
            val remainder = cleanText.substringAfter(splitWord).trim()
            if (remainder.isEmpty()) {
                playBeepSound()
                speakConfirmation("Sí, te escucho.", "Yes, I am listening.")
                // Immediate listen trigger for actual command
                cancelListeningInternal()
                startListeningInternal()
                return
            }
            remainder
        } else {
            lastTriggerTime = now // Reset conversation window upon successful continuation
            cleanText
        }

        Log.d("VoiceCommand", "Evaluating phrase: \"$phraseToProcess\"")
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "es") ?: "es"

        if (lang == "en") {
            when {
                phraseToProcess.contains("change style") || phraseToProcess.contains("next style") || phraseToProcess.contains("change gauge") -> {
                    speakConfirmation("Changing gauge style.", "Changing gauge style.")
                    onCommandRecognized?.invoke(VoiceCommand.CYCLE_GAUGES)
                }
                phraseToProcess.contains("telemetry") || phraseToProcess.contains("speedometer") || phraseToProcess.contains("gauges") || phraseToProcess.contains("dashboard") -> {
                    speakConfirmation("Opening telemetry dashboard.", "Opening telemetry dashboard.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_DASHBOARD)
                }
                phraseToProcess.contains("scan") || phraseToProcess.contains("diagnostic") || phraseToProcess.contains("show codes") || phraseToProcess.contains("faults") || phraseToProcess.contains("dtc") -> {
                    speakConfirmation("Opening diagnostics.", "Opening diagnostics.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_DIAGNOSTICS)
                }
                phraseToProcess.contains("oscilloscope") || phraseToProcess.contains("scope") -> {
                    speakConfirmation("Opening oscilloscope.", "Opening oscilloscope.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_OSCILLOSCOPE)
                }
                phraseToProcess.contains("locator") || phraseToProcess.contains("engine") || phraseToProcess.contains("3d") -> {
                    speakConfirmation("Opening 3D component locator.", "Opening 3D component locator.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_LOCATOR)
                }
                phraseToProcess.contains("settings") || phraseToProcess.contains("config") -> {
                    speakConfirmation("Opening settings.", "Opening settings.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_SETTINGS)
                }
                phraseToProcess.contains("coolant") || phraseToProcess.contains("engine temp") || phraseToProcess.contains("temp") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_TEMPERATURE)
                }
                phraseToProcess.contains("battery") || phraseToProcess.contains("voltage") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_VOLTAGE)
                }
                phraseToProcess.contains("rpm") || phraseToProcess.contains("speed") || phraseToProcess.contains("revolutions") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_RPM)
                }
                phraseToProcess.contains("clear codes") || phraseToProcess.contains("clear faults") || phraseToProcess.contains("clear dtc") -> {
                    speakConfirmation("Requesting fault code clear.", "Requesting fault code clear.")
                    onCommandRecognized?.invoke(VoiceCommand.CLEAR_DTCS)
                }
                phraseToProcess.contains("disable voice") || phraseToProcess.contains("mute alerts") -> {
                    speakConfirmation("Voice alerts muted.", "Voice alerts muted.")
                    onCommandRecognized?.invoke(VoiceCommand.DISABLE_ALERTS)
                }
                phraseToProcess.contains("enable voice") || phraseToProcess.contains("unmute alerts") -> {
                    speakConfirmation("Voice alerts enabled.", "Voice alerts enabled.")
                    onCommandRecognized?.invoke(VoiceCommand.ENABLE_ALERTS)
                }
                phraseToProcess.contains("turn off copilot") || phraseToProcess.contains("stop copilot") || phraseToProcess.contains("bye") -> {
                    speakConfirmation("Goodbye. Deactivating copilot.", "Goodbye. Deactivating copilot.")
                    onCommandRecognized?.invoke(VoiceCommand.DEACTIVATE_COPILOT)
                }
                // New 100% Info coverage commands
                phraseToProcess.contains("diagnose") || phraseToProcess.contains("report status") || phraseToProcess.contains("ecu status") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_DIAGNOSTICS)
                }
                phraseToProcess.contains("fuel") || phraseToProcess.contains("consumption") || phraseToProcess.contains("economy") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_FUEL_ECONOMY)
                }
                phraseToProcess.contains("dna") || phraseToProcess.contains("signature") || phraseToProcess.contains("behavior") || phraseToProcess.contains("health") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_VEHICLE_DNA)
                }
                phraseToProcess.contains("perito") || phraseToProcess.contains("inspection") || phraseToProcess.contains("valuation") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_PERITO_REPORT)
                }
                phraseToProcess.contains("summary") || phraseToProcess.contains("general status") || phraseToProcess.contains("overall") || phraseToProcess.contains("car status") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_GENERAL_STATUS)
                }
            }
        } else {
            // Spanish commands
            when {
                phraseToProcess.contains("cambiar estilo") || phraseToProcess.contains("siguiente estilo") || phraseToProcess.contains("cambiar reloj") || phraseToProcess.contains("otro estilo") -> {
                    speakConfirmation("Cambiando estilo de relojes.", "Changing gauge style.")
                    onCommandRecognized?.invoke(VoiceCommand.CYCLE_GAUGES)
                }
                phraseToProcess.contains("telemetría") || phraseToProcess.contains("velocímetro") || phraseToProcess.contains("relojes") || phraseToProcess.contains("dashboard") || phraseToProcess.contains("pantalla principal") -> {
                    speakConfirmation("Abriendo panel de telemetría.", "Opening telemetry dashboard.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_DASHBOARD)
                }
                phraseToProcess.contains("escanear") || phraseToProcess.contains("diagnóstico") || phraseToProcess.contains("ver fallas") || phraseToProcess.contains("códigos") || phraseToProcess.contains("ver códigos") || phraseToProcess.contains("fallas") -> {
                    speakConfirmation("Abriendo diagnóstico de fallas.", "Opening diagnostics.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_DIAGNOSTICS)
                }
                phraseToProcess.contains("osciloscopio") -> {
                    speakConfirmation("Abriendo osciloscopio.", "Opening oscilloscope.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_OSCILLOSCOPE)
                }
                phraseToProcess.contains("localizador") || phraseToProcess.contains("ver motor") || phraseToProcess.contains("motor 3d") || phraseToProcess.contains("piezas") -> {
                    speakConfirmation("Abriendo localizador de componentes 3D.", "Opening 3D component locator.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_LOCATOR)
                }
                phraseToProcess.contains("ajustes") || phraseToProcess.contains("configuración") -> {
                    speakConfirmation("Abriendo ajustes avanzados.", "Opening settings.")
                    onCommandRecognized?.invoke(VoiceCommand.NAVIGATE_SETTINGS)
                }
                phraseToProcess.contains("temperatura") || phraseToProcess.contains("caliente") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_TEMPERATURE)
                }
                phraseToProcess.contains("voltaje") || phraseToProcess.contains("batería") || phraseToProcess.contains("alternador") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_VOLTAGE)
                }
                phraseToProcess.contains("rpm") || phraseToProcess.contains("revoluciones") || phraseToProcess.contains("giro") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_RPM)
                }
                phraseToProcess.contains("borrar fallas") || phraseToProcess.contains("borrar códigos") || phraseToProcess.contains("limpiar códigos") -> {
                    speakConfirmation("Borrando códigos de falla de la ECU.", "Requesting fault code clear.")
                    onCommandRecognized?.invoke(VoiceCommand.CLEAR_DTCS)
                }
                phraseToProcess.contains("apagar alertas") || phraseToProcess.contains("desactivar voz") || phraseToProcess.contains("silenciar alertas") -> {
                    speakConfirmation("Alertas de voz desactivadas.", "Voice alerts muted.")
                    onCommandRecognized?.invoke(VoiceCommand.DISABLE_ALERTS)
                }
                phraseToProcess.contains("encender alertas") || phraseToProcess.contains("activar voz") || phraseToProcess.contains("hablar alertas") -> {
                    speakConfirmation("Alertas de voz activadas.", "Voice alerts enabled.")
                    onCommandRecognized?.invoke(VoiceCommand.ENABLE_ALERTS)
                }
                phraseToProcess.contains("apagar copiloto") || phraseToProcess.contains("desactivar copiloto") || phraseToProcess.contains("adiós") -> {
                    speakConfirmation("Hasta luego. Desactivando copiloto por voz.", "Goodbye. Deactivating copilot.")
                    onCommandRecognized?.invoke(VoiceCommand.DEACTIVATE_COPILOT)
                }
                // New 100% Info coverage commands
                phraseToProcess.contains("diagnóstico completo") || phraseToProcess.contains("verificar fallas") || phraseToProcess.contains("estado ecu") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_DIAGNOSTICS)
                }
                phraseToProcess.contains("combustible") || phraseToProcess.contains("consumo") || phraseToProcess.contains("economía") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_FUEL_ECONOMY)
                }
                phraseToProcess.contains("dna") || phraseToProcess.contains("firma") || phraseToProcess.contains("comportamiento") || phraseToProcess.contains("salud") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_VEHICLE_DNA)
                }
                phraseToProcess.contains("perito") || phraseToProcess.contains("peritaje") || phraseToProcess.contains("inspección") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_PERITO_REPORT)
                }
                phraseToProcess.contains("resumen") || phraseToProcess.contains("estado general") || phraseToProcess.contains("información del auto") -> {
                    onCommandRecognized?.invoke(VoiceCommand.SAY_GENERAL_STATUS)
                }
            }
        }
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("VoiceCommand", "Recognizer ready for speech.")
    }

    override fun onBeginningOfSpeech() {
        Log.d("VoiceCommand", "Speech started.")
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        Log.d("VoiceCommand", "Speech ended.")
        isRecognizerActive = false
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error"
        }
        Log.w("VoiceCommand", "SpeechRecognizer error: $message ($error)")
        isRecognizerActive = false

        // Loop restart
        if (isListening && !voiceFeedbackManager.isSpeaking.value) {
            val retryDelay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 500L else 300L
            mainHandler.postDelayed({
                if (isListening && !isRecognizerActive && !voiceFeedbackManager.isSpeaking.value) {
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        cancelListeningInternal()
                    }
                    startListeningInternal()
                }
            }, retryDelay)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            processSpokenText(spokenText)
        }
        isRecognizerActive = false

        // Restart listening to achieve continuous recognition
        if (isListening && !voiceFeedbackManager.isSpeaking.value) {
            mainHandler.postDelayed({
                if (isListening && !isRecognizerActive && !voiceFeedbackManager.isSpeaking.value) {
                    startListeningInternal()
                }
            }, 300)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].lowercase().trim()
            val hasWakeWord = text.contains("elysium") || text.contains("elísium")
            
            // Check if wake-word is newly heard to play the beep and wake up early
            if (hasWakeWord && (System.currentTimeMillis() - lastTriggerTime > 4000L)) {
                Log.d("VoiceCommand", "Wake word 'Elysium' heard in partial results. Activating listener force.")
                lastTriggerTime = System.currentTimeMillis()
                playBeepSound()
                
                // Force reset listener to clear the microphone buffer and prepare it with full sensitivity for the command
                cancelListeningInternal()
                startListeningInternal()
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
