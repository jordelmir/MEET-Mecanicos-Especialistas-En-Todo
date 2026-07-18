package com.elysium369.meet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.ai.data.AiRepository
import com.elysium369.meet.ai.domain.*
import com.elysium369.meet.automotive.parts.AutomotivePart
import com.elysium369.meet.automotive.parts.ProcedureKnowledgeBase
import com.elysium369.meet.automotive.parts.RegionalSynonymResolver
import com.elysium369.meet.automotive.parts.ResolvedAlias
import com.elysium369.meet.core.ai.ChatMessage
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.QosMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SupportChatViewModel — Copiloto IA & Soporte.
 *
 * Wired to the new multi-provider AiRepository engine, which uses
 * MiniMax-M1 as the default provider (with debug API key fallback).
 * The old SharedPreferences-based GeminiDiagnostic is no longer used.
 */
@HiltViewModel
class SupportChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val obdSession: ObdSession,
    private val knowledgeBase: ProcedureKnowledgeBase
) : ViewModel() {

    companion object {
        private const val TAG = "SupportChatVM"
        private const val DEFAULT_PROVIDER = "minimax"
        private const val DEFAULT_MODEL = "MiniMax-M1"
    }

    private val synonymResolver = RegionalSynonymResolver()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val liveData: StateFlow<Map<String, Float>> = obdSession.liveData
    val connectionState: StateFlow<ObdState> = obdSession.state
    val qosMetrics: StateFlow<QosMetrics> = obdSession.qosMetrics

    private val _oscilloscopeBuffer = MutableStateFlow<Map<String, List<Pair<Long, Float>>>>(emptyMap())
    val oscilloscopeBuffer: StateFlow<Map<String, List<Pair<Long, Float>>>> = _oscilloscopeBuffer.asStateFlow()

    init {
        // Collect high frequency liveData to feed the oscilloscope buffer
        viewModelScope.launch {
            obdSession.liveData.collect { data ->
                val timestamp = System.currentTimeMillis()
                val current = _oscilloscopeBuffer.value.toMutableMap()
                data.forEach { (pid, value) ->
                    val list = current[pid]?.toMutableList() ?: mutableListOf()
                    list.add(Pair(timestamp, value))
                    if (list.size > 200) {
                        list.removeAt(0)
                    }
                    current[pid] = list
                }
                _oscilloscopeBuffer.value = current
            }
        }
    }

    fun sendMessage(content: String, vehicleInfo: String) {
        if (content.isBlank()) return

        val userMessage = ChatMessage("user", content)
        _messages.value = _messages.value + userMessage
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val liveDataSnapshot = obdSession.liveData.value.mapValues { "%.2f".format(it.value) }

                // Build list of AiMessages from conversation history
                val aiMessages = _messages.value.map { msg ->
                    AiMessage(
                        role = if (msg.role == "user") AiRole.USER else AiRole.ASSISTANT,
                        content = msg.content
                    )
                }

                // RAG: Resolve regional synonyms and inject local knowledge
                val resolvedAlias = synonymResolver.resolve(content)
                val knowledgeContext = if (resolvedAlias != null) {
                    val part = knowledgeBase.getPart(resolvedAlias.partId)
                    if (part != null) {
                        buildKnowledgeInjection(part, resolvedAlias)
                    } else {
                        ""
                    }
                } else {
                    val matchedParts = knowledgeBase.searchParts(content)
                    if (matchedParts.isNotEmpty()) {
                        buildKnowledgeInjection(matchedParts.first(), null)
                    } else {
                        ""
                    }
                }

                // If we have local knowledge, augment the last user message
                val augmentedMessages = if (knowledgeContext.isNotBlank()) {
                    aiMessages.dropLast(1) + AiMessage(
                        role = AiRole.USER,
                        content = knowledgeContext + "\n\n" + content
                    )
                } else {
                    aiMessages
                }

                // Select the right feature based on whether we detected a part query
                val feature = if (resolvedAlias != null || knowledgeContext.isNotBlank()) {
                    AiFeature.REPAIR_GUIDE
                } else {
                    AiFeature.AI_COPILOT
                }

                val request = AiRequest(
                    feature = feature,
                    providerId = DEFAULT_PROVIDER,
                    model = DEFAULT_MODEL,
                    messages = augmentedMessages,
                    temperature = 0.4,
                    maxTokens = 4096,
                    context = buildAiContext(vehicleInfo, liveDataSnapshot)
                )

                val result = aiRepository.complete(request)

                result.fold(
                    onSuccess = { response ->
                        Log.d(TAG, "AI response OK (${response.usage?.totalTokens ?: 0} tokens, provider=${response.providerId})")
                        _messages.value = _messages.value + ChatMessage("model", response.text)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "AI error", error)
                        val errorText = when (error) {
                            is AiError.ProviderUnavailable -> "⚠️ Proveedor IA no disponible. Verifica la configuración."
                            is AiError.RateLimited -> "⏳ Límite de uso alcanzado. Intenta de nuevo en un momento."
                            is AiError.PolicyBlocked -> "🛡️ Solicitud bloqueada por política de seguridad."
                            is AiError.MissingApiKey -> "🔑 API Key no configurada. Ve a Ajustes > Motor IA."
                            is AiError.InvalidApiKey -> "🔑 API Key inválida o revocada."
                            is AiError.NetworkUnavailable -> "🌐 Sin conexión a internet."
                            is AiError.HttpFailure -> "🌐 Error HTTP ${error.code}. ${error.safeBody ?: ""}"
                            is AiError.Timeout -> "⏱️ Tiempo de espera agotado. Intenta de nuevo."
                            is AiError.MalformedResponse -> "⚠️ Respuesta inesperada del proveedor."
                            else -> "❌ Error: ${error.localizedMessage ?: "Error desconocido"}"
                        }
                        _messages.value = _messages.value + ChatMessage("model", errorText)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in sendMessage", e)
                _messages.value = _messages.value + ChatMessage(
                    "model",
                    "❌ Error inesperado: ${e.localizedMessage ?: "Error desconocido"}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun analyzeWaveform(pid: String, vehicleInfo: String) {
        val buffer = _oscilloscopeBuffer.value[pid] ?: return
        if (buffer.isEmpty()) return

        val friendlyName = when (pid) {
            "010C" -> "RPM del Motor"
            "0105" -> "Temperatura de Refrigerante (ECT)"
            "0111" -> "Posición del Acelerador (TPS)"
            "0142" -> "Voltaje de Batería"
            else -> "Sensor $pid"
        }

        _messages.value = _messages.value + ChatMessage("user", "Analiza la forma de onda del sensor: $friendlyName ($pid)")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val liveDataSnapshot = obdSession.liveData.value.mapValues { "%.2f".format(it.value) }

                // Build waveform context for the AI
                val waveformSummary = buildWaveformAnalysisPrompt(pid, friendlyName, buffer)

                val aiMessages = listOf(
                    AiMessage(AiRole.USER, waveformSummary)
                )

                val request = AiRequest(
                    feature = AiFeature.OSCILLOSCOPE_ANALYSIS,
                    providerId = DEFAULT_PROVIDER,
                    model = DEFAULT_MODEL,
                    messages = aiMessages,
                    temperature = 0.2,
                    maxTokens = 4096,
                    context = buildAiContext(vehicleInfo, liveDataSnapshot)
                )

                val result = aiRepository.complete(request)

                result.fold(
                    onSuccess = { response ->
                        _messages.value = _messages.value + ChatMessage("model", response.text)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Waveform analysis error", error)
                        _messages.value = _messages.value + ChatMessage(
                            "model",
                            "❌ Error en análisis de forma de onda: ${error.localizedMessage ?: "Error desconocido"}"
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in analyzeWaveform", e)
                _messages.value = _messages.value + ChatMessage(
                    "model",
                    "❌ Error inesperado: ${e.localizedMessage ?: "Error desconocido"}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun buildAiContext(
        vehicleInfo: String,
        liveDataSnapshot: Map<String, String>
    ): AiContext {
        val pidReadings = liveDataSnapshot.map { (pid, value) ->
            PidReading(
                pid = pid,
                name = pidFriendlyName(pid),
                value = value,
                unit = pidUnit(pid)
            )
        }
        // Parse vehicleInfo ("Make Model Year") into parts
        val parts = vehicleInfo.split(" ", limit = 3)
        val vehicleContext = if (parts.size >= 2) {
            VehicleContext(
                make = parts.getOrElse(0) { "" },
                model = parts.getOrElse(1) { "" },
                year = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
                engine = ""
            )
        } else {
            VehicleContext(make = vehicleInfo, model = "", year = 0, engine = "")
        }

        return AiContext(
            vehicle = vehicleContext,
            obd = ObdContext(
                connected = obdSession.state.value == ObdState.CONNECTED,
                activePidsCount = liveDataSnapshot.size
            ),
            dtcs = emptyList(),
            livePids = pidReadings,
            manualAvailability = null,
            appModule = "copilot",
            locale = "es-MX",
            userRole = UserRole.MECHANIC,
            safetyMode = true
        )
    }

    private fun buildWaveformAnalysisPrompt(
        pid: String,
        friendlyName: String,
        buffer: List<Pair<Long, Float>>
    ): String {
        val values = buffer.map { it.second }
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val avg = if (values.isNotEmpty()) values.average() else 0.0
        val stdDev = if (values.size > 1) {
            val mean = values.average()
            kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average())
        } else 0.0

        // Sample 20 representative points
        val step = (buffer.size / 20).coerceAtLeast(1)
        val samples = buffer.filterIndexed { i, _ -> i % step == 0 }.take(20)
        val sampleStr = samples.joinToString(", ") { "%.2f".format(it.second) }

        return """
            Analiza la siguiente señal de osciloscopio capturada en tiempo real del sensor $friendlyName (PID: $pid):
            
            📊 Estadísticas:
            - Muestras totales: ${buffer.size}
            - Mín: %.2f | Máx: %.2f | Promedio: %.2f | Desv. Est.: %.2f
            - Rango temporal: ${if (buffer.size >= 2) "${(buffer.last().first - buffer.first().first) / 1000}s" else "N/A"}
            
            📈 Muestra representativa (20 puntos):
            $sampleStr
            
            Diagnostica anomalías, estabilidad, patrones irregulares y posibles fallas.
        """.trimIndent().format(min, max, avg, stdDev)
    }

    private fun buildKnowledgeInjection(
        part: AutomotivePart,
        resolvedAlias: ResolvedAlias?
    ): String {
        return buildString {
            appendLine("=== CONOCIMIENTO TÉCNICO LOCAL (BASE DE DATOS VERIFICADA) ===")
            if (resolvedAlias != null) {
                appendLine("🔍 Sinónimo regional detectado → Pieza canónica: ${part.canonicalNameEs} (${part.canonicalNameEn})")
                appendLine("   Sistema: ${part.system.name} | Confianza: ${resolvedAlias.confidence}")
            }
            appendLine("Pieza: ${part.canonicalNameEs} (${part.canonicalNameEn})")
            appendLine("Sistema: ${part.system.name} | Subsistema: ${part.subsystem}")
            appendLine("Descripción: ${part.description}")
            appendLine("Aliases conocidos: ${part.aliases.joinToString(", ")}")
            appendLine("Síntomas de falla: ${part.symptoms.joinToString("; ")}")
            appendLine("DTCs relacionados: ${part.relatedDtcs.joinToString(", ")}")
            appendLine("Herramientas requeridas: ${part.requiredTools.joinToString(", ")}")
            appendLine("Nivel de seguridad: ${part.safetyLevel.name}")
            if (part.procedures.isNotEmpty()) {
                val proc = part.procedures.first()
                appendLine("\n--- PROCEDIMIENTO TÉCNICO ---")
                appendLine("Título: ${proc.title}")
                appendLine("Dificultad: ${proc.difficulty}")
                appendLine("Tiempo estimado: ${proc.estimatedTimeMinutes} minutos")
                appendLine("Requiere elevador: ${if (proc.requiresLift) "SÍ" else "NO"}")
                appendLine("Requiere alineación: ${if (proc.requiresAlignment) "SÍ" else "NO"}")
                appendLine("\nAntes de empezar:")
                proc.beforeStart.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
                appendLine("\nPasos:")
                proc.steps.forEachIndexed { i, step -> appendLine("  ${i + 1}. $step") }
                if (proc.torqueSpecs.isNotEmpty()) {
                    appendLine("\nTorques:")
                    proc.torqueSpecs.forEach { ts ->
                        appendLine("  - ${ts.description}: ${ts.torqueNm} Nm (${ts.torqueFtLbs} ft-lbs)${ts.angleDegrees?.let { " + ${it}°" } ?: ""}")
                    }
                }
                appendLine("\nErrores comunes:")
                proc.commonMistakes.forEachIndexed { i, m -> appendLine("  ${i + 1}. $m") }
                appendLine("\nValidación post-reparación:")
                proc.postRepairValidation.forEachIndexed { i, v -> appendLine("  ${i + 1}. $v") }
                appendLine("\n⚠️ ${proc.whenToStopWarning}")
                appendLine("💬 Explicación al cliente: ${proc.customerExplanation}")
            }
            if (part.notes.isNotEmpty()) {
                appendLine("\nNotas adicionales:")
                part.notes.forEach { appendLine("  • $it") }
            }
            appendLine("=== FIN CONOCIMIENTO LOCAL ===")
            appendLine("\nIMPORTANTE: Usa la información anterior como base técnica. NO inventes torques ni especificaciones que no estén aquí. Si no hay dato, indica 'consultar manual OEM'.")
        }
    }

    private fun pidFriendlyName(pid: String): String = when (pid) {
        "010C" -> "RPM Motor"
        "010D" -> "Velocidad Vehículo"
        "0105" -> "Temp. Refrigerante"
        "010F" -> "Temp. Aire Admisión"
        "0111" -> "Posición Acelerador"
        "0142" -> "Voltaje Batería"
        "0104" -> "Carga Motor"
        "010E" -> "Avance Encendido"
        else -> "PID $pid"
    }

    private fun pidUnit(pid: String): String = when (pid) {
        "010C" -> "RPM"
        "010D" -> "km/h"
        "0105", "010F" -> "°C"
        "0111", "0104" -> "%"
        "0142" -> "V"
        "010E" -> "°"
        else -> ""
    }
}
