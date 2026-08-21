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
open class VoiceFeedbackManager @Inject constructor(
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

    open fun speak(es: String, en: String = es, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Domain Voice Guidance Copilot (Viajes, Grúas, Mecánicos, Repuestos, Servicios & Ferretería)
    // ─────────────────────────────────────────────────────────────────────────────

    fun guideRideStatus(step: String, driverName: String? = null, plate: String? = null, etaMinutes: Int? = null) {
        when (step.uppercase()) {
            "SEARCHING" -> speak(
                es = "Buscando conductores certificados cercanos para tu viaje seguro.",
                en = "Searching for nearby certified drivers for your ride."
            )
            "DRIVER_ASSIGNED" -> {
                val driverText = if (!driverName.isNullOrBlank()) "con $driverName" else ""
                val plateText = if (!plate.isNullOrBlank()) ", vehículo placa $plate" else ""
                val etaText = if (etaMinutes != null) ", tiempo estimado de llegada $etaMinutes minutos" else ""
                speak(
                    es = "Conductor asignado $driverText $plateText $etaText. Tu viaje está protegido por telemetría Elysium.",
                    en = "Driver assigned $driverText $plateText $etaText. Your trip is protected by Elysium telemetry."
                )
            }
            "IN_PROGRESS" -> speak(
                es = "Viaje iniciado. Monitoreando ruta, velocidad y seguridad en tiempo real.",
                en = "Trip started. Monitoring route, speed and security in real time."
            )
            "ARRIVED" -> speak(
                es = "Has llegado a tu destino. Gracias por viajar con Elysium Mobility.",
                en = "You have arrived at your destination. Thank you for riding with Elysium Mobility."
            )
        }
    }

    fun guideTowTruckStatus(step: String, driverName: String? = null, etaMinutes: Int? = null, truckType: String? = null) {
        when (step.uppercase()) {
            "SEARCHING" -> speak(
                es = "Localizando unidades de grúa de plataforma y arrastre en tu zona geográfica.",
                en = "Locating flatbed and towing units in your geographic area."
            )
            "ASSIGNED" -> {
                val nameText = if (!driverName.isNullOrBlank()) "a cargo de $driverName" else ""
                val etaText = if (etaMinutes != null) ", llegada estimada en $etaMinutes minutos" else ""
                val typeText = if (!truckType.isNullOrBlank()) "de tipo $truckType" else "asistencia vial"
                speak(
                    es = "Grúa $typeText confirmada $nameText $etaText. Mantén las luces de emergencia encendidas.",
                    en = "Tow truck confirmed $nameText $etaText. Please keep hazard lights active."
                )
            }
            "ON_SITE" -> speak(
                es = "Unidad de asistencia vial en sitio. Procediendo a maniobra de aseguramiento del vehículo.",
                en = "Roadside assistance unit on site. Securing vehicle for transit."
            )
        }
    }

    fun guideMechanicStatus(step: String, shopName: String? = null, dtcCode: String? = null) {
        when (step.uppercase()) {
            "SEARCHING" -> {
                val dtcText = if (!dtcCode.isNullOrBlank()) "para el código de falla $dtcCode" else ""
                speak(
                    es = "Buscando mecánicos y talleres certificados especialistas $dtcText.",
                    en = "Searching for certified mechanics and workshops specialized $dtcText."
                )
            }
            "BID_RECEIVED" -> {
                val nameText = if (!shopName.isNullOrBlank()) "de $shopName" else ""
                speak(
                    es = "Has recibido una nueva propuesta técnica $nameText con garantía de mano de obra y evidencia OBD.",
                    en = "You received a new technical proposal $nameText with warranty and OBD evidence."
                )
            }
            "DIAGNOSIS_STARTED" -> speak(
                es = "Diagnóstico mecánico en proceso. Registrando lecturas de sensores y evidencia fotográfica.",
                en = "Mechanical diagnosis in progress. Capturing sensor telemetry and photo evidence."
            )
        }
    }

    fun guidePartsMarketplaceStatus(step: String, partName: String? = null, offersCount: Int? = null, bestPrice: Double? = null) {
        when (step.uppercase()) {
            "SEARCHING" -> {
                val partText = if (!partName.isNullOrBlank()) "para $partName" else "de tu solicitud"
                speak(
                    es = "Publicando solicitud $partText con verificación de compatibilidad exacta por VIN.",
                    en = "Publishing parts request $partText with exact VIN compatibility verification."
                )
            }
            "OFFERS_RECEIVED" -> {
                val countText = if (offersCount != null && offersCount > 1) "$offersCount cotizaciones disponibles" else "nueva cotización disponible"
                val priceText = if (bestPrice != null) ", mejor precio desde ¢${String.format("%,.0f", bestPrice)}" else ""
                speak(
                    es = "Repuesteras han ofertado piezas compatibles. $countText $priceText.",
                    en = "Auto parts stores submitted bids. $countText $priceText."
                )
            }
        }
    }

    fun guideHardwareAndTradesStatus(step: String, materialName: String? = null, hasHardwareOffer: Boolean = false, hasTradeOffer: Boolean = false) {
        when (step.uppercase()) {
            "WELCOME" -> speak(
                es = "Sección Ferretería y Servicios del Hogar activa. Puedes solicitar materiales de ferretería y plomeros o electricistas ofrecerán su instalación profesional.",
                en = "Hardware and Trades Marketplace active. Request materials and certified plumbers or electricians will offer professional installation."
            )
            "REQUEST_PUBLISHED" -> {
                val matText = if (!materialName.isNullOrBlank()) "de $materialName" else "de materiales"
                speak(
                    es = "Solicitud $matText en subasta abierta. Notificando a ferreterías y profesionales de instalación de tu zona.",
                    en = "Hardware request in open auction. Notifying local hardware stores and certified trade installers."
                )
            }
            "DUAL_OFFER_READY" -> speak(
                es = "Tienes ofertas disponibles: Ferretería ofrece los materiales y el especialista técnico ofrece la mano de obra. Puedes elegir el Combo Llave en Mano con descuento.",
                en = "Dual bids available: Hardware store offers the materials and the technician offers installation. Turnkey combo discount available."
            )
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
