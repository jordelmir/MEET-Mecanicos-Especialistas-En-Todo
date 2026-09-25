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

    fun hasEntitlement(entitlementId: String?): Boolean {
        if (entitlementId == null) return true // Free capability
        return _userEntitlements.value.contains(entitlementId)
    }

    fun grantEntitlement(entitlementId: String) {
        _userEntitlements.update { it + entitlementId }
    }

    fun revokeEntitlement(entitlementId: String) {
        _userEntitlements.update { it - entitlementId }
    }

    fun loadEntitlementsForPrincipal(principalId: String, granted: Set<String>) {
        _userEntitlements.value = granted
    }
}
