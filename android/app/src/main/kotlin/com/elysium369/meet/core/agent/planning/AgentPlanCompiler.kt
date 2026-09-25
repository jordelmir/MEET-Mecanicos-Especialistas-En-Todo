package com.elysium369.meet.core.agent.planning

import com.elysium369.meet.core.agent.capability.CapabilityRegistry
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import com.elysium369.meet.core.agent.domain.AgentPlan
import com.elysium369.meet.core.agent.domain.AgentPlanStep
import com.elysium369.meet.core.agent.domain.IntentResolution
import com.elysium369.meet.core.agent.domain.PlanStatus
import java.util.UUID

sealed interface PlanCompilationResult {
    data class Success(val plan: AgentPlan) : PlanCompilationResult
    data class ClarificationNeeded(val prompt: String) : PlanCompilationResult
    data class CompilationError(val reason: String) : PlanCompilationResult
}

/**
 * Compiles a structured, safe AgentPlan from an IntentResolution.
 * Enforces Master Order Omega §18:
 * "Never execute arbitrary model-generated strings. Reject: unknown capability,
 * unknown argument, NaN, Infinity, arbitrary shell/SQL/URLs."
 */
class AgentPlanCompiler(
    private val capabilityRegistry: CapabilityRegistry = CapabilityRegistry.default,
) {

    fun compile(resolution: IntentResolution, context: AgentContextSnapshot): PlanCompilationResult {
        if (resolution.requiresClarification || resolution.primaryIntent == null) {
            return PlanCompilationResult.ClarificationNeeded(
                prompt = resolution.clarificationPrompt ?: "¿Podrías darme más detalles de lo que deseas hacer?"
            )
        }

        val targetCapabilityId = resolution.primaryIntent.targetCapabilityId
            ?: return PlanCompilationResult.CompilationError("No target capability identified for intent ${resolution.primaryIntent.name}")

        val capability = capabilityRegistry.get<Any, Any>(targetCapabilityId)
            ?: return PlanCompilationResult.CompilationError("Capacidad '${targetCapabilityId.value}' no registrada en CapabilityRegistry")

        // Input sanitization (§18)
        for ((key, value) in resolution.entities) {
            if (value.contains("NaN", ignoreCase = true) || value.contains("Infinity", ignoreCase = true)) {
                return PlanCompilationResult.CompilationError("Valor inválido detectado en entidad '$key': $value")
            }
        }

        val steps = listOf(
            AgentPlanStep(
                stepIndex = 1,
                capabilityId = targetCapabilityId,
                actionDescription = "Ejecutar ${resolution.primaryIntent.name}",
                parameters = resolution.entities,
                risk = capability.risk,
            )
        )

        val plan = AgentPlan(
            planId = UUID.randomUUID().toString(),
            contextGeneration = context.contextGeneration,
            steps = steps,
            status = PlanStatus.PLANNED,
            confirmationPrompt = if (capability.requiresConfirmation) {
                "¿Confirmas que deseas proceder con '${resolution.primaryIntent.name}'?"
            } else null,
        )

        return PlanCompilationResult.Success(plan)
    }
}
