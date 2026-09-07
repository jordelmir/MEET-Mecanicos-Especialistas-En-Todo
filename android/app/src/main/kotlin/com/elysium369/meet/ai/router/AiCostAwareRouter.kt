package com.elysium369.meet.ai.router

import com.elysium369.meet.ai.AiTaskClass
import com.elysium369.meet.ai.domain.AiCommercialPlan
import com.elysium369.meet.ai.domain.AiRouteDecision
import com.elysium369.meet.ai.domain.AiRoutingContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCostAwareRouter @Inject constructor() {

    fun route(ctx: AiRoutingContext): AiRouteDecision {
        if (ctx.caseCostMicrosUsd >= ctx.caseBudgetMicrosUsd) {
            return AiRouteDecision(
                providerId = "local",
                modelId = "local-deterministic",
                reasoningEffort = "none",
                maxOutputTokens = 100,
                allowEscalation = false,
                reason = "BUDGET_EXHAUSTED"
            )
        }

        return when (ctx.taskClass) {
            AiTaskClass.DTC_SUMMARY.name -> AiRouteDecision(
                providerId = "openai",
                modelId = "gpt-5.4-nano",
                reasoningEffort = "low",
                maxOutputTokens = 700,
                allowEscalation = false,
                reason = "DTC_SUMMARY_NANO_LOW"
            )
            AiTaskClass.CATALOG_MAPPING.name -> AiRouteDecision(
                providerId = "openai",
                modelId = "gpt-5.4-nano",
                reasoningEffort = "low",
                maxOutputTokens = 600,
                allowEscalation = false,
                reason = "CATALOG_MAPPING_NANO_LOW"
            )
            AiTaskClass.DEEP_REPAIR_REASONING.name -> {
                val isHighComplexity = ctx.complexity.equals("HIGH", ignoreCase = true)
                val allowEscalation = ctx.plan != AiCommercialPlan.FREE

                if (isHighComplexity && allowEscalation && (ctx.caseBudgetMicrosUsd - ctx.caseCostMicrosUsd > 50_000)) {
                    AiRouteDecision(
                        providerId = "openai",
                        modelId = "gpt-5.4-mini",
                        reasoningEffort = "medium",
                        maxOutputTokens = 3000,
                        allowEscalation = true,
                        reason = "COMPLEX_REPAIR_ESCALATED_MINI"
                    )
                } else {
                    AiRouteDecision(
                        providerId = "openai",
                        modelId = "gpt-5.4-nano",
                        reasoningEffort = if (isHighComplexity) "high" else "medium",
                        maxOutputTokens = 2500,
                        allowEscalation = allowEscalation,
                        reason = "DEEP_REPAIR_NANO_CHAMPION"
                    )
                }
            }
            else -> AiRouteDecision(
                providerId = "openai",
                modelId = "gpt-5.4-nano",
                reasoningEffort = "medium",
                maxOutputTokens = 1500,
                allowEscalation = ctx.plan != AiCommercialPlan.FREE,
                reason = "DEFAULT_NANO_CHAMPION"
            )
        }
    }
}
