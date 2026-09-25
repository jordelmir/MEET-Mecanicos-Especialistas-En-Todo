package com.elysium369.meet.core.agent.intent

import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import com.elysium369.meet.core.agent.domain.AgentInput
import com.elysium369.meet.core.agent.domain.AgentIntent
import com.elysium369.meet.core.agent.domain.IntentResolution
import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.LayaQuestion

/**
 * Calibrated Intent & Entity Resolver powered by Laya AI (System 1).
 * Replaces proprietary Jev with open-weights System 1 decision engine.
 * Master Order Omega §15, §16.
 */
class LayaIntentResolver(
    private val decisionEngine: LayaDecisionEngine = LayaDecisionEngine(),
    private val deterministicFastPath: DeterministicIntentResolver = DeterministicIntentResolver(),
) : IntentResolver {

    private val supportedCapabilities = listOf(
        "ride.request",
        "vehicle.read_dtc",
        "emissions.analyze_o2",
        "guide.teach_mode",
        "safety.alert",
        "roadside.tow",
    )

    override suspend fun resolve(input: AgentInput, context: AgentContextSnapshot): IntentResolution {
        // 1. Check deterministic fast-path first (< 1ms for exact aliases like "mi casa" -> HOME)
        val fastResolution = deterministicFastPath.resolve(input, context)
        if (fastResolution.primaryIntent != null && !fastResolution.requiresClarification) {
            return fastResolution
        }

        // 2. Evaluate with Laya AI System 1 non-autoregressive decision engine
        val questions = listOf(
            LayaQuestion.Choice(name = "capability", options = supportedCapabilities),
            LayaQuestion.Noul(name = "is_emergency"),
        )

        val batch = decisionEngine.evaluate(input.rawText, questions)
        val bestChoice = batch.choice("capability")
        val isEmergency = batch.noul("is_emergency")?.value ?: false

        if (isEmergency) {
            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = "safety.alert",
                    targetDomain = "safety",
                    targetCapabilityId = CapabilityId.of("safety.report"),
                    parameters = mapOf("query" to input.rawText),
                    calibratedConfidence = 0.99,
                ),
                reasonCode = "LAYA_EMERGENCY_DETECTED",
            )
        }

        if (bestChoice != null && bestChoice.confidence >= 0.35) {
            val targetCap = bestChoice.value
            val targetDomain = targetCap.substringBefore(".")
            return IntentResolution(
                input = input,
                primaryIntent = AgentIntent(
                    name = targetCap,
                    targetDomain = targetDomain,
                    targetCapabilityId = CapabilityId.of(targetCap),
                    parameters = mapOf("query" to input.rawText),
                    calibratedConfidence = bestChoice.confidence,
                ),
                reasonCode = "LAYA_SYSTEM_ONE_RESOLVED",
            )
        }

        return fastResolution
    }
}
