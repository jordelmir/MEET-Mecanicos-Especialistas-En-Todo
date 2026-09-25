package com.elysium369.meet.core.agentstore.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.agentstore.data.AgentCatalogRepository
import com.elysium369.meet.core.agentstore.data.AgentEntitlementRepository
import com.elysium369.meet.core.agentstore.domain.AgentManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _selectedAgent = MutableStateFlow<AgentManifest?>(null)
    val selectedAgent: StateFlow<AgentManifest?> = _selectedAgent.asStateFlow()

    private val _previewModalOpen = MutableStateFlow(false)
    val previewModalOpen: StateFlow<Boolean> = _previewModalOpen.asStateFlow()

    val storeItems: StateFlow<List<AgentStoreItemUi>> = combine(
        catalogRepository.agents,
        entitlementRepository.userEntitlements,
    ) { agents, entitlements ->
        agents.map { agent ->
            AgentStoreItemUi(
                manifest = agent,
                isOwned = agent.isFree || (agent.requiredEntitlement != null && agent.requiredEntitlement in entitlements),
                isCurrentActive = agent.id == "agent.evair_core",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectAgent(agent: AgentManifest) {
        _selectedAgent.value = agent
    }

    fun openPreview(agent: AgentManifest) {
        _selectedAgent.value = agent
        _previewModalOpen.value = true
    }

    fun closePreview() {
        _previewModalOpen.value = false
    }

    fun purchaseAndUnlock(agent: AgentManifest) {
        viewModelScope.launch {
            val entitlement = agent.requiredEntitlement
            if (entitlement != null) {
                entitlementRepository.grantEntitlement(entitlement)
            }
        }
    }
}
