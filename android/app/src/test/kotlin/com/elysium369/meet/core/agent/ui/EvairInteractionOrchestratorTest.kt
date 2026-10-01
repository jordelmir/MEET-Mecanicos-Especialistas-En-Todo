package com.elysium369.meet.core.agent.ui

import com.elysium369.meet.core.agent.capability.*
import com.elysium369.meet.core.agent.context.*
import com.elysium369.meet.core.agent.domain.*
import com.elysium369.meet.core.agent.intent.IntentResolver
import com.elysium369.meet.core.audio.VoiceTranscriptEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EvairInteractionOrchestratorTest {
    private val capabilityId = CapabilityId("test.inspect")
    private val transcript = VoiceTranscriptEvent.Final("test-utterance", "zeta tarea acotada", 1f)
    private fun provider(principal: String) = object : AgentContextProvider {
        override fun currentSnapshot() = AgentContextSnapshot(principalId = principal, activeVehicleId = "vehicle-real")
    }
    private val resolver = object : IntentResolver {
        override suspend fun resolve(input: AgentInput, context: AgentContextSnapshot) = IntentResolution(
            input, AgentIntent("inspect", "test", capabilityId), entities = mapOf("query" to input.rawText))
    }

    @Test fun absentRegistryPreservesFallbackAndExplainsUnavailable() = runBlocking {
        val result = EvairInteractionOrchestrator(contextProvider = provider("account-real"),
            capabilityRegistry = CapabilityRegistry(), intentResolver = resolver).handleFinalTranscript(transcript)
        assertTrue(result is EvairInteractionResult.Unhandled)
        assertNotNull((result as EvairInteractionResult.Unhandled).unavailableMessage)
    }

    @Test fun registeredCapabilityProducesDeferredPlanWithoutCallingExecutor() = runBlocking {
        var executed = false
        var seenPrincipal: String? = null
        val registry = CapabilityRegistry().apply {
            register(object : AgentCapability<Map<String, String>, String> {
                override val id = capabilityId
                override val risk = AgentRisk.COMMITTING
                override suspend fun validate(input: Map<String, String>, context: AgentExecutionContext) = CapabilityValidation.Valid
                override suspend fun execute(input: Map<String, String>, context: AgentExecutionContext): AgentResult<String> {
                    executed = true
                    return AgentResult.Success("unexpected", "unexpected")
                }
            })
        }
        val checkingResolver = object : IntentResolver {
            override suspend fun resolve(input: AgentInput, context: AgentContextSnapshot): IntentResolution {
                seenPrincipal = context.principalId
                return resolver.resolve(input, context)
            }
        }
        val result = EvairInteractionOrchestrator(contextProvider = provider("account-real"),
            capabilityRegistry = registry, intentResolver = checkingResolver).handleFinalTranscript(transcript)
        assertEquals("account-real", seenPrincipal)
        assertFalse(executed)
        assertTrue(result is EvairInteractionResult.GoalDeferred)
        assertNotNull((result as EvairInteractionResult.GoalDeferred).plan.confirmationPrompt)
        assertTrue(result.message.contains("no se ha ejecutado"))
    }
}
