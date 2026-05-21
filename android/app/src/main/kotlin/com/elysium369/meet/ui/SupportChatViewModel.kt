package com.elysium369.meet.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.ai.ChatMessage
import com.elysium369.meet.core.ai.GeminiDiagnostic
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.QosMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupportChatViewModel @Inject constructor(
    private val geminiDiagnostic: GeminiDiagnostic,
    private val obdSession: ObdSession,
    @ApplicationContext private val context: Context
) : ViewModel() {

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
        // Load AI config from shared prefs if exists
        val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val apiKey = sharedPrefs.getString("ai_api_key", null)
        val baseUrl = sharedPrefs.getString("ai_base_url", null)
        if (!apiKey.isNullOrEmpty()) {
            geminiDiagnostic.updateConfig(apiKey, baseUrl)
        }

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
            val liveDataSnapshot = obdSession.liveData.value.mapValues { "%.2f".format(it.value) }
            
            // Re-check config in case it changed in another screen
            val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val apiKey = sharedPrefs.getString("ai_api_key", null)
            val baseUrl = sharedPrefs.getString("ai_base_url", null)
            geminiDiagnostic.updateConfig(apiKey, baseUrl)

            val response = geminiDiagnostic.chat(
                history = _messages.value,
                vehicleInfo = vehicleInfo,
                liveData = liveDataSnapshot
            )
            
            _messages.value = _messages.value + ChatMessage("model", response)
            _isLoading.value = false
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
            val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            val apiKey = sharedPrefs.getString("ai_api_key", null)
            val baseUrl = sharedPrefs.getString("ai_base_url", null)
            geminiDiagnostic.updateConfig(apiKey, baseUrl)

            val result = geminiDiagnostic.analyzeLiveTelemetry(
                vehicleInfo = vehicleInfo,
                oscilloscopeData = mapOf(pid to buffer)
            )

            _messages.value = _messages.value + ChatMessage("model", result.analysisText)
            _isLoading.value = false
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }
}

