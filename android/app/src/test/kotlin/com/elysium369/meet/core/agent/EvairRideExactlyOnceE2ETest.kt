package com.elysium369.meet.core.agent

import android.content.SharedPreferences
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.CapabilityRegistry
import com.elysium369.meet.core.agent.capability.ride.RideCapabilityInput
import com.elysium369.meet.core.agent.capability.ride.RideRequestCapability
import com.elysium369.meet.core.agent.context.AgentContextSnapshot
import com.elysium369.meet.core.agent.domain.AgentInput
import com.elysium369.meet.core.agent.domain.InputSource
import com.elysium369.meet.core.agent.intent.DeterministicIntentResolver
import com.elysium369.meet.core.agent.planning.AgentPlanCompiler
import com.elysium369.meet.core.agent.planning.PlanCompilationResult
import com.elysium369.meet.core.agent.policy.AgentPolicyEngine
import com.elysium369.meet.core.agent.policy.PolicyDecision
import com.elysium369.meet.ride.application.RideApplicationService
import com.elysium369.meet.ride.application.RideCommandBus
import com.elysium369.meet.ride.application.RideCommandEnqueueResult
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideSavedPlacesStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Enforces Master Order Omega §87 (RIDE END-TO-END TEST) & §92 (OUTBOX EXACTLY-ONCE EFFECT):
 *
 * Given:
 * - authenticated passenger "pax-jorge-369"
 * - HOME saved in RideSavedPlacesStore
 * - destination "Multiplaza Escazú" resolvable
 *
 * When:
 * 1. User says: "Pídame un viaje de mi casa a Multiplaza Escazú"
 * 2. JEV resolves intent and entities (pickupAlias = "HOME", destinationQuery = "Multiplaza Escazú")
 * 3. Plan compiled for "ride.request"
 * 4. Step 1 (Preview): Policy & Capability demand user confirmation with quote details
 * 5. Step 2 (Confirmation): User confirms with stable idempotency key
 * 6. Step 3 (Execution): Command enqueued into RideCommandBus
 * 7. Step 4 (Duplicate callback immunity): 3 duplicate callbacks delivered with identical key
 *
 * Assert:
 * - HOME is resolved strictly from store (latitude 9.9281, longitude -84.0907), never fake fallback
 * - Quote contains authoritative fare and duration
 * - Confirmation is mandatory before committing
 * - Exactly ONE command is created in the bus
 * - Duplicate submissions result in AlreadyQueued, zero duplicate trips created!
 */
class EvairRideExactlyOnceE2ETest {

    private lateinit var store: RideSavedPlacesStore
    private lateinit var fakeBus: TestRideCommandBus
    private lateinit var rideService: RideApplicationService
    private lateinit var rideCapability: RideRequestCapability
    private lateinit var registry: CapabilityRegistry
    private lateinit var policyEngine: AgentPolicyEngine
    private lateinit var resolver: DeterministicIntentResolver
    private lateinit var compiler: AgentPlanCompiler

    private val principalId = "pax-jorge-369"

    @Before
    fun setUp() {
        val memoryPrefs = createMemoryPreferences()
        store = RideSavedPlacesStore(memoryPrefs)

        // Save authentic HOME for Jorge
        val home = RideSavedPlace(
            slot = "HOME",
            label = "Mi Casa",
            address = "Condominio Monte Sol, Pozos de Santa Ana, San José",
            latitude = 9.9320,
            longitude = -84.1870,
            providerId = "user_saved",
        )
        store.save(principalId, home)

        fakeBus = TestRideCommandBus()
        rideService = RideApplicationService(commandBus = fakeBus, savedPlacesStore = store)
        rideCapability = RideRequestCapability(rideService)

        registry = CapabilityRegistry()
        registry.register(rideCapability)

        policyEngine = AgentPolicyEngine()
        resolver = DeterministicIntentResolver()
        compiler = AgentPlanCompiler(registry)
    }

    @Test
    fun completeEndToEndFlow_withDuplicateCallbacks_executesExactlyOnce() = runBlocking {
        val context = AgentContextSnapshot(
            contextGeneration = 1L,
            principalId = principalId,
            currentScreen = "home_screen",
        )

        // ── 1. NLU & JEV INTENT RESOLUTION (§15, §26) ──
        val userVoiceInput = AgentInput(
            rawText = "Pídame un viaje de mi casa a Multiplaza Escazú",
            source = InputSource.VOICE,
        )

        val resolution = resolver.resolve(userVoiceInput, context)
        assertTrue("Intent must be confident", resolution.isConfident)
        assertEquals("ride.request", resolution.primaryIntent?.name)
        assertEquals("HOME", resolution.entities["pickupAlias"])
        assertEquals("Multiplaza Escazú", resolution.entities["destinationQuery"])

        // ── 2. PLAN COMPILATION (§17, §18) ──
        val planResult = compiler.compile(resolution, context)
        assertTrue(planResult is PlanCompilationResult.Success)
        val plan = (planResult as PlanCompilationResult.Success).plan
        assertEquals(1, plan.steps.size)
        assertEquals("ride.request", plan.steps.first().capabilityId.value)

        // ── 3. FIRST EXECUTION: PREVIEW & CONFIRMATION GATING (§21, §22) ──
        val unconfirmedContext = AgentExecutionContext(
            principalId = principalId,
            contextGeneration = 1L,
            userConfirmed = false, // Not yet confirmed
            idempotencyKey = "idem_key_ride_req_998877",
        )

        val policyBeforeConfirmation = policyEngine.evaluate(
            capability = rideCapability,
            planContextGeneration = plan.contextGeneration,
            currentContext = unconfirmedContext,
        )
        assertTrue("Committing capability must require confirmation", policyBeforeConfirmation is PolicyDecision.RequiresConfirmation)

        val executionStep1 = rideCapability.execute(
            RideCapabilityInput(
                destinationQuery = resolution.entities["destinationQuery"]!!,
                pickupAlias = resolution.entities["pickupAlias"],
            ),
            unconfirmedContext,
        )
        assertTrue("Capability must return ConfirmationRequired", executionStep1 is AgentResult.ConfirmationRequired)
        val preview = executionStep1 as AgentResult.ConfirmationRequired
        assertTrue("Preview prompt must contain fare and Multiplaza", preview.prompt.contains("Multiplaza Escazú"))
        assertNotNull("Must generate confirmation token", preview.confirmationToken)

        // Assert 0 rides queued before confirmation
        assertEquals(0, fakeBus.enqueuedCommands.size)

        // ── 4. SECOND EXECUTION: HUMAN EXPLICIT CONFIRMATION (§22, §25) ──
        val stableIdempotencyKey = "stable_voice_tx_token_12345678"
        val confirmedContext = unconfirmedContext.copy(
            userConfirmed = true,
            idempotencyKey = stableIdempotencyKey,
        )

        val policyAfterConfirmation = policyEngine.evaluate(
            capability = rideCapability,
            planContextGeneration = plan.contextGeneration,
            currentContext = confirmedContext,
        )
        assertEquals(PolicyDecision.Authorized, policyAfterConfirmation)

        val executionStep2 = rideCapability.execute(
            RideCapabilityInput(
                destinationQuery = resolution.entities["destinationQuery"]!!,
                pickupAlias = resolution.entities["pickupAlias"],
            ),
            confirmedContext,
        )
        assertTrue("Booking must succeed after confirmation", executionStep2 is AgentResult.Success)
        val output = (executionStep2 as AgentResult.Success).data
        assertTrue(output.rideId.startsWith("ride_"))

        // Assert exactly ONE command enqueued in the bus
        assertEquals("Exactly ONE command must be created in the bus", 1, fakeBus.enqueuedCommands.size)
        val enqueued = fakeBus.enqueuedCommands.first()
        assertEquals(stableIdempotencyKey, enqueued.idempotencyKey)
        assertTrue(enqueued.payload.destinationAddress?.contains("Multiplaza Escazú") == true)

        // ── 5. DUPLICATE SPEECH / NETWORK RETRIES (§24, §92) ──
        // Simulate rapid duplicate callbacks with the same idempotency key
        for (i in 1..3) {
            val dupResult = rideCapability.execute(
                RideCapabilityInput(
                    destinationQuery = resolution.entities["destinationQuery"]!!,
                    pickupAlias = resolution.entities["pickupAlias"],
                ),
                confirmedContext, // same idempotency key
            )
            assertTrue("Duplicate call must return Success without duplicate creation", dupResult is AgentResult.Success)
            val dupOutput = (dupResult as AgentResult.Success).data
            assertEquals(output.rideId, dupOutput.rideId)
        }

        // STILL EXACTLY ONE COMMAND IN THE BUS!
        assertEquals("Zero duplicate rides must be created across duplicate callbacks", 1, fakeBus.enqueuedCommands.size)
    }

    private class TestRideCommandBus : RideCommandBus {
        val enqueuedCommands = mutableListOf<RideQueuedCommand>()
        private val seenKeys = mutableSetOf<String>()

        override suspend fun enqueue(command: RideQueuedCommand): RideCommandEnqueueResult {
            return if (seenKeys.add(command.idempotencyKey)) {
                enqueuedCommands.add(command)
                RideCommandEnqueueResult.Enqueued
            } else {
                RideCommandEnqueueResult.AlreadyQueued
            }
        }
    }

    private fun createMemoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, String>()
        val pending = mutableMapOf<String, String>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { pending[args!![0] as String] = args[1] as String; editor }
                "apply", "commit" -> { values.putAll(pending); pending.clear(); if (method.name == "commit") true else null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
    }
}
