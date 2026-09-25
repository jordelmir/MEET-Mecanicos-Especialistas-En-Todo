package com.elysium369.meet.core.agent.context

import kotlinx.serialization.Serializable

/**
 * Immutable snapshot of runtime context used for plan generation, JEV entity resolution,
 * and concurrency freshness validation.
 * Master Order Omega §15, §16, §23.
 */
@Serializable
data class AgentContextSnapshot(
    val contextGeneration: Long = 1L,
    val principalId: String,
    val activeVehicleId: String? = null,
    val currentScreen: String? = null,
    val activeRideId: String? = null,
    val activeDtcCodes: List<String> = emptyList(),
    val isObdConnected: Boolean = false,
    val userEntitlements: Set<String> = emptySet(),
    val savedPlaceAliases: Map<String, String> = emptyMap(),
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Contract to obtain fresh runtime context across app components.
 */
interface AgentContextProvider {
    fun currentSnapshot(): AgentContextSnapshot
}
