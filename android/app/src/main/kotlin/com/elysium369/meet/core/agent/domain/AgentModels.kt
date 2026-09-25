package com.elysium369.meet.core.agent.domain

import com.elysium369.meet.core.agent.capability.AgentRisk
import com.elysium369.meet.core.agent.capability.CapabilityId
import java.util.UUID
import kotlinx.serialization.Serializable

enum class InputSource {
    VOICE,
    TEXT,
    SEMANTIC_TAP,
    SENSOR_TRIGGER,
}

@Serializable
data class AgentInput(
    val rawText: String,
    val source: InputSource = InputSource.TEXT,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class AgentIntent(
    val name: String,
    val targetDomain: String,
    val targetCapabilityId: CapabilityId? = null,
    val parameters: Map<String, String> = emptyMap(),
    val calibratedConfidence: Double = 1.0,
)

@Serializable
data class AgentEntity(
    val key: String,
    val value: String,
    val entityType: String = "STRING",
)

@Serializable
data class IntentResolution(
    val input: AgentInput,
    val primaryIntent: AgentIntent?,
    val candidateIntents: List<AgentIntent> = emptyList(),
    val entities: Map<String, String> = emptyMap(),
    val ambiguities: List<String> = emptyList(),
    val requiresClarification: Boolean = false,
    val clarificationPrompt: String? = null,
    val reasonCode: String = "INTENT_RESOLVED",
) {
    val isConfident: Boolean
        get() = primaryIntent != null && primaryIntent.calibratedConfidence >= 0.75 && !requiresClarification
}

enum class PlanStatus {
    PLANNED,
    IN_CONFIRMATION,
    EXECUTING,
    COMPLETED,
    ABORTED,
    INVALIDATED_STALE,
}

@Serializable
data class AgentPlanStep(
    val stepIndex: Int,
    val capabilityId: CapabilityId,
    val actionDescription: String,
    val parameters: Map<String, String> = emptyMap(),
    val risk: AgentRisk = AgentRisk.READ_ONLY,
)

@Serializable
data class AgentPlan(
    val planId: String = UUID.randomUUID().toString(),
    val contextGeneration: Long,
    val steps: List<AgentPlanStep>,
    val status: PlanStatus = PlanStatus.PLANNED,
    val confirmationPrompt: String? = null,
    val createdEpochMs: Long = System.currentTimeMillis(),
)
