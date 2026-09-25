package com.elysium369.meet.core.agentstore.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentEntitlementRepository @Inject constructor() {

    private val _userEntitlements = MutableStateFlow<Set<String>>(emptySet())
    val userEntitlements: StateFlow<Set<String>> = _userEntitlements.asStateFlow()

    private val _equippedAgentId = MutableStateFlow("agent.evair_core")
    val equippedAgentId: StateFlow<String> = _equippedAgentId.asStateFlow()

    fun hasEntitlement(entitlementId: String?): Boolean {
        if (entitlementId == null) return true // Free capability
        return _userEntitlements.value.contains(entitlementId)
    }

    fun isAgentOwned(agentId: String, requiredEntitlement: String?): Boolean {
        if (requiredEntitlement == null || agentId == "agent.evair_core") return true
        return _userEntitlements.value.contains(requiredEntitlement)
    }

    fun grantEntitlement(entitlementId: String) {
        _userEntitlements.update { it + entitlementId }
    }

    fun revokeEntitlement(entitlementId: String) {
        _userEntitlements.update { it - entitlementId }
    }

    fun equipAgent(agentId: String) {
        _equippedAgentId.value = agentId
    }

    fun loadEntitlementsForPrincipal(principalId: String, granted: Set<String>) {
        _userEntitlements.value = granted
    }
}
