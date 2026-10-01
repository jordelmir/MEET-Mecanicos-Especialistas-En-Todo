package com.elysium369.meet.core.agent.planning

import com.elysium369.meet.core.agent.capability.CapabilityDescriptor
import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import java.util.UUID

data class HumanGoal(
    val goalId: String = UUID.randomUUID().toString(),
    val naturalLanguageText: String,
    val targetDomain: String? = null,
    val priority: Int = 1,
)

sealed interface GoalPlanResult {
    data class Success(val executionPlan: CapabilityExecutionPlan) : GoalPlanResult
    data class CycleDetected(val cycleNodes: List<CapabilityId>) : GoalPlanResult
    data class DepthLimitExceeded(val maxDepth: Int) : GoalPlanResult
    data class MissingPrerequisite(val capabilityId: CapabilityId, val missingPrerequisiteId: CapabilityId) : GoalPlanResult
    data class CompilationError(val reason: String) : GoalPlanResult
}

data class CapabilityExecutionPlan(
    val planId: String = UUID.randomUUID().toString(),
    val goal: HumanGoal,
    val steps: List<CapabilityPlanNode>,
    val estimatedRisk: com.elysium369.meet.core.agent.capability.AgentRisk,
    val requiresExplicitConfirmation: Boolean,
)

data class CapabilityPlanNode(
    val stepIndex: Int,
    val capability: CapabilityDescriptor,
    val parameters: Map<String, String>,
    val dependsOnStepIndices: List<Int>,
)

class GoalCompiler(
    private val capabilities: Map<CapabilityId, CapabilityDescriptor>,
    private val maxDepth: Int = 10,
) {
    fun compile(
        goal: HumanGoal,
        targetCapabilityId: CapabilityId,
        context: AgentContextSnapshot,
    ): GoalPlanResult {
        val target = capabilities[targetCapabilityId]
            ?: return GoalPlanResult.CompilationError("Capacidad no encontrada: ${targetCapabilityId.value}")

        val resolvedSteps = mutableListOf<CapabilityDescriptor>()
        val visited = mutableSetOf<CapabilityId>()
        val recursionStack = mutableSetOf<CapabilityId>()

        fun resolveDfs(current: CapabilityDescriptor, depth: Int): GoalPlanResult? {
            if (depth > maxDepth) {
                return GoalPlanResult.DepthLimitExceeded(maxDepth)
            }
            if (current.id in recursionStack) {
                return GoalPlanResult.CycleDetected(recursionStack.toList() + current.id)
            }
            if (current.id in visited) return null

            recursionStack.add(current.id)

            for (prereqId in current.prerequisites) {
                val prereq = capabilities[prereqId]
                    ?: return GoalPlanResult.MissingPrerequisite(current.id, prereqId)

                val err = resolveDfs(prereq, depth + 1)
                if (err != null) return err
            }

            recursionStack.remove(current.id)
            visited.add(current.id)
            resolvedSteps.add(current)
            return null
        }

        val error = resolveDfs(target, 1)
        if (error != null) return error

        val planNodes = resolvedSteps.mapIndexed { index, cap ->
            val dependsOn = cap.prerequisites.mapNotNull { prereqId ->
                val prereqIndex = resolvedSteps.indexOfFirst { it.id == prereqId }
                if (prereqIndex >= 0) prereqIndex + 1 else null
            }
            CapabilityPlanNode(
                stepIndex = index + 1,
                capability = cap,
                parameters = emptyMap(),
                dependsOnStepIndices = dependsOn,
            )
        }

        val highestRisk = resolvedSteps.map { it.risk }.maxByOrNull { it.ordinal }
            ?: com.elysium369.meet.core.agent.capability.AgentRisk.READ_ONLY

        val requiresConfirmation = highestRisk != com.elysium369.meet.core.agent.capability.AgentRisk.READ_ONLY

        return GoalPlanResult.Success(
            CapabilityExecutionPlan(
                goal = goal,
                steps = planNodes,
                estimatedRisk = highestRisk,
                requiresExplicitConfirmation = requiresConfirmation,
            )
        )
    }
}
