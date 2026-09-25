package com.elysium369.meet.core.agentstore.data

import com.elysium369.meet.core.agentstore.domain.AgentManifest
import com.elysium369.meet.core.agentstore.domain.OfficialAgents
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AgentCatalogRepository @Inject constructor() {

    private val _agents = MutableStateFlow(OfficialAgents.ALL)
    val agents: Flow<List<AgentManifest>> = _agents.asStateFlow()

    fun getAgentById(id: String): AgentManifest? {
        return OfficialAgents.ALL.firstOrNull { it.id == id }
    }

    fun listByCategory(category: com.elysium369.meet.core.agentstore.domain.AgentCategory?): List<AgentManifest> {
        if (category == null) return OfficialAgents.ALL
        return OfficialAgents.ALL.filter { it.category == category }
    }
}
