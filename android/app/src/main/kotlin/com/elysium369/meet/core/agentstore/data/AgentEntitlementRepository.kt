package com.elysium369.meet.core.agentstore.data

import com.elysium369.meet.core.agentstore.domain.AgentEntitlementGateway
import com.elysium369.meet.core.agentstore.domain.EntitlementState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   A G E N T   E N T I T L E M E N T   R E P O S I T O R Y
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §2
 *
 *  STRICT FAIL-CLOSED SPECIFICATION:
 *  - NO local hardcoded `return true`.
 *  - NO local `grantEntitlement()` method in production.
 *  - UNKNOWN entitlement -> premium denied.
 *  - Backend unavailable -> premium denied.
 *  - Local tamper -> irrelevant against server verification.
 * ══════════════════════════════════════════════════════════════════════
 */
@Singleton
class AgentEntitlementRepository @Inject constructor(
    private val gateway: AgentEntitlementGateway,
) {

    private val _state = MutableStateFlow<EntitlementState>(EntitlementState.Unknown)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private val _equippedAgentId = MutableStateFlow("agent.evair_core")
    val equippedAgentId: StateFlow<String> = _equippedAgentId.asStateFlow()

    private val _userEntitlements = MutableStateFlow<Set<String>>(emptySet())
    val userEntitlements: StateFlow<Set<String>> = _userEntitlements.asStateFlow()

    private val refreshGeneration = java.util.concurrent.atomic.AtomicLong(0)

    suspend fun refresh() {
        val generation = refreshGeneration.incrementAndGet()
        val owner = gateway.currentPrincipalId()
        _state.value = EntitlementState.Unknown
        _userEntitlements.value = emptySet()
        if (owner == null) return
        val outcome = gateway.fetchAuthoritativeEntitlements()
        if (gateway.currentPrincipalId() != owner || refreshGeneration.get() != generation) return
        _state.value = outcome.fold(
            onSuccess = { snapshot ->
                if (snapshot.principalId != owner) return@fold EntitlementState.Unavailable("ACCOUNT_MISMATCH")
                _userEntitlements.value = snapshot.entitlements
                EntitlementState.Available(snapshot)
            },
            onFailure = { error ->
                _userEntitlements.value = emptySet()
                EntitlementState.Unavailable(code = "ENTITLEMENT_FETCH_FAILED")
            },
        )
    }

    /**
     * Fail-closed check: returns true ONLY if entitlementId is null (free feature)
     * OR the authoritative snapshot is Available and contains the required entitlement.
     */
    fun hasEntitlement(entitlementId: String?): Boolean {
        if (entitlementId == null) {
            return true
        }
        val current = _state.value
        if (current is EntitlementState.Available && current.snapshot.principalId != gateway.currentPrincipalId()) {
            _state.value = EntitlementState.Unknown
            _userEntitlements.value = emptySet()
            _equippedAgentId.value = "agent.evair_core"
            return false
        }
        return current is EntitlementState.Available && entitlementId in current.snapshot.entitlements
    }

    fun isAgentOwned(agentId: String, requiredEntitlement: String?): Boolean {
        if (requiredEntitlement == null) {
            return true
        }
        return hasEntitlement(requiredEntitlement)
    }

    fun equipAgent(agentId: String) {
        _equippedAgentId.value = agentId
    }
}
