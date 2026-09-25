package com.elysium369.meet.core.agent.capability

import com.elysium369.meet.core.plugin.CapabilityRisk
import kotlinx.serialization.Serializable

/**
 * Type alias unifying AgentRisk with the foundational CapabilityRisk contract.
 * Master Order Omega §19, §20, §21.
 */
typealias AgentRisk = CapabilityRisk

/**
 * Strongly typed capability identifier following domain.action pattern.
 * Examples: "ride.request", "vehicle.read_dtc", "emissions.analyze_o2@1".
 */
@Serializable
@JvmInline
value class CapabilityId(val value: String) {
    init {
        require(value.isNotBlank()) { "CapabilityId cannot be blank" }
        require(value.matches(Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+(@\\d+)?$"))) {
            "CapabilityId must follow domain.action format (e.g. 'ride.request', 'vehicle.read_dtc@1'), got '$value'"
        }
    }

    val domain: String get() = value.substringBefore('.')
    val action: String get() = value.substringAfter('.').substringBefore('@')

    companion object {
        fun of(value: String): CapabilityId = CapabilityId(value)
    }
}

/**
 * Authoritative execution context passed into every capability validation and execution.
 * Tracks principal, context freshness generation, entitlements, and idempotency key.
 */
@Serializable
data class AgentExecutionContext(
    val principalId: String,
    val activeVehicleId: String? = null,
    val currentScreen: String? = null,
    val contextGeneration: Long = 1L,
    val userEntitlements: Set<String> = emptySet(),
    val grantedPermissions: Set<String> = emptySet(),
    val userConfirmed: Boolean = false,
    val idempotencyKey: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

sealed interface CapabilityValidation {
    data object Valid : CapabilityValidation
    data class Invalid(val reason: String, val missingFields: List<String> = emptyList()) : CapabilityValidation
}

sealed interface AgentResult<out T> {
    data class Success<T>(val data: T, val summary: String) : AgentResult<T>
    data class ConfirmationRequired(
        val prompt: String,
        val payloadPreview: Map<String, String>,
        val risk: AgentRisk,
        val confirmationToken: String,
    ) : AgentResult<Nothing>
    data class Denied(val reason: String, val risk: AgentRisk) : AgentResult<Nothing>
    data class StaleContext(val expectedGeneration: Long, val actualGeneration: Long) : AgentResult<Nothing>
    data class EntitlementRequired(val requiredEntitlement: String, val storeDeepLink: String = "agent_store") : AgentResult<Nothing>
    data class Failure(val code: String, val message: String) : AgentResult<Nothing>
}

/**
 * Universal capability contract for the Elysium AI OS.
 * The registered capability owns its risk classification, confirmation policy, and idempotency semantics.
 * Master Order Omega §19, §20.
 */
interface AgentCapability<I : Any, O : Any> {
    val id: CapabilityId
    val risk: AgentRisk
    val requiredEntitlement: String? get() = null
    val requiresConfirmation: Boolean get() = risk in setOf(
        AgentRisk.COMMITTING,
        AgentRisk.FINANCIAL,
        AgentRisk.SAFETY_CRITICAL,
        AgentRisk.VEHICLE_CRITICAL
    )
    val requiresIdempotency: Boolean get() = risk in setOf(
        AgentRisk.COMMITTING,
        AgentRisk.FINANCIAL,
        AgentRisk.SAFETY_CRITICAL
    )

    suspend fun validate(input: I, context: AgentExecutionContext): CapabilityValidation
    suspend fun execute(input: I, context: AgentExecutionContext): AgentResult<O>
}
