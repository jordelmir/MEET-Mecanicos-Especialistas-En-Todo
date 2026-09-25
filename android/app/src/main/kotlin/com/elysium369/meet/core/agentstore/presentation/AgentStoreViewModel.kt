package com.elysium369.meet.core.agentstore.presentation

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.agent.laya.EvairDigiSoulEngine
import com.elysium369.meet.core.agentstore.data.AgentCatalogRepository
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentCategory
import com.elysium369.meet.core.agentstore.domain.AgentManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class AgentStoreItemUi(
    val manifest: AgentManifest,
    val isOwned: Boolean,
    val isCurrentActive: Boolean = false,
)

@HiltViewModel
class AgentStoreViewModel @Inject constructor(
    private val catalogRepository: AgentCatalogRepository,
    private val entitlementRepository: AgentEntitlementRepository,
    @ApplicationContext private val context: Context,
) : ViewModel(), TextToSpeech.OnInitListener {

    private val _selectedCategory = MutableStateFlow<AgentCategory?>(null)
    val selectedCategory: StateFlow<AgentCategory?> = _selectedCategory.asStateFlow()

    private val _selectedAgent = MutableStateFlow<AgentManifest?>(null)
    val selectedAgent: StateFlow<AgentManifest?> = _selectedAgent.asStateFlow()

    private val _previewModalOpen = MutableStateFlow(false)
    val previewModalOpen: StateFlow<Boolean> = _previewModalOpen.asStateFlow()

    private val _isVoicePlaying = MutableStateFlow(false)
    val isVoicePlaying: StateFlow<Boolean> = _isVoicePlaying.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var voiceJob: Job? = null

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (_: Exception) {
            isTtsInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "CR")
            isTtsInitialized = true
        }
    }

    val equippedAgentId: StateFlow<String> = entitlementRepository.equippedAgentId

    val storeItems: StateFlow<List<AgentStoreItemUi>> = combine(
        catalogRepository.agents,
        entitlementRepository.userEntitlements,
        entitlementRepository.equippedAgentId,
        _selectedCategory,
    ) { agents, entitlements, activeId, categoryFilter ->
        val filtered = if (categoryFilter == null) {
            agents
        } else {
            agents.filter { it.category == categoryFilter }
        }

        filtered.map { agent ->
            AgentStoreItemUi(
                manifest = agent,
                isOwned = agent.isFree || (agent.requiredEntitlement != null && agent.requiredEntitlement in entitlements),
                isCurrentActive = agent.id == activeId,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val equippedAgent: StateFlow<AgentManifest?> = combine(
        catalogRepository.agents,
        entitlementRepository.equippedAgentId,
    ) { agents, activeId ->
        agents.firstOrNull { it.id == activeId } ?: agents.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectCategory(category: AgentCategory?) {
        _selectedCategory.value = category
    }

    fun openPreview(agent: AgentManifest) {
        _selectedAgent.value = agent
        _previewModalOpen.value = true
    }

    fun closePreview() {
        stopVoiceSample()
        _previewModalOpen.value = false
    }

    fun playVoiceSample(text: String, pitch: Float = 1.0f, speechRate: Float = 1.0f) {
        stopVoiceSample()
        _isVoicePlaying.value = true

        voiceJob = viewModelScope.launch {
            if (isTtsInitialized && tts != null) {
                tts?.setPitch(pitch)
                tts?.setSpeechRate(speechRate)
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sample_${System.currentTimeMillis()}")
                val durationEstimateMs = (text.length * 75L).coerceIn(2000L, 7000L)
                delay(durationEstimateMs)
            } else {
                // Fallback animated waveform simulation
                delay(3500L)
            }
            _isVoicePlaying.value = false
        }
    }

    fun stopVoiceSample() {
        voiceJob?.cancel()
        voiceJob = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isVoicePlaying.value = false
    }

    fun equipAgent(agent: AgentManifest) {
        viewModelScope.launch {
            entitlementRepository.equipAgent(agent.id)
            // Sync with DigiSoul companion state
            try {
                EvairDigiSoulEngine.shared.recordInteraction(
                    action = "AGENT_EQUIPPED_${agent.id.uppercase()}",
                    xpGained = 25,
                    narrative = "El vehículo ahora cuenta con la presencia de ${agent.displayName} (${agent.subtitle})."
                )
            } catch (_: Exception) {}
        }
    }

    fun purchaseAndUnlock(agent: AgentManifest) {
        viewModelScope.launch {
            val entitlement = agent.requiredEntitlement
            if (entitlement != null) {
                entitlementRepository.grantEntitlement(entitlement)
            }
            equipAgent(agent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopVoiceSample()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
