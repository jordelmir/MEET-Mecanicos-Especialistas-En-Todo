package com.elysium369.meet.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.ai.ChatMessage
import com.elysium369.meet.core.ai.GeminiDiagnostic
import com.elysium369.meet.core.obd.ObdSession
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

    init {
        // Load AI config from shared prefs if exists
        val sharedPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val apiKey = sharedPrefs.getString("ai_api_key", null)
        val baseUrl = sharedPrefs.getString("ai_base_url", null)
        if (!apiKey.isNullOrEmpty()) {
            geminiDiagnostic.updateConfig(apiKey, baseUrl)
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

    fun clearChat() {
        _messages.value = emptyList()
    }
}
