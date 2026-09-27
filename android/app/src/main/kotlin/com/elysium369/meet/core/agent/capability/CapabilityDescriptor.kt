package com.elysium369.meet.core.agent.capability

import kotlinx.serialization.Serializable

@Serializable
data class CapabilityInputSchema(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
)

@Serializable
data class CapabilityOutputSchema(
    val name: String,
    val type: String,
    val description: String,
)

data class CapabilityDescriptor(
    val id: CapabilityId,
    val domain: String,
    val name: String,
    val description: String,
    val risk: AgentRisk,
    val requiredEntitlement: String? = null,
    val prerequisites: List<CapabilityId> = emptyList(),
    val inputs: List<CapabilityInputSchema> = emptyList(),
    val outputs: List<CapabilityOutputSchema> = emptyList(),
    val isIdempotent: Boolean = true,
)
