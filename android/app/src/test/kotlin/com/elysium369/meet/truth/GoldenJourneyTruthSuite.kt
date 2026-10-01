package com.elysium369.meet.truth

import android.content.SharedPreferences
import androidx.compose.ui.geometry.Rect
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.ride.RideCapabilityInput
import com.elysium369.meet.core.agent.capability.ride.RideRequestCapability
import com.elysium369.meet.core.agent.ui.AgentTextFieldRole
import com.elysium369.meet.core.agent.ui.AgentUiControlId
import com.elysium369.meet.core.agent.ui.AgentUiControlKind
import com.elysium369.meet.core.agent.ui.AgentUiControlSnapshot
import com.elysium369.meet.core.agent.ui.AgentUiRegistry
import com.elysium369.meet.core.agent.ui.AgentUiSensitivity
import com.elysium369.meet.core.agent.ui.RegisteredAgentUiControl
import com.elysium369.meet.core.agent.ui.UiTargetResolutionResult
import com.elysium369.meet.core.agent.ui.UiTargetResolver
import com.elysium369.meet.core.agent.ui.VoiceBindingResult
import com.elysium369.meet.core.agent.ui.VoiceFormBinder
import com.elysium369.meet.ride.application.PlaceResolutionResult
import com.elysium369.meet.ride.application.RideApplicationService
import com.elysium369.meet.ride.application.RideCommandBus
import com.elysium369.meet.ride.application.RideCommandEnqueueResult
import com.elysium369.meet.ride.application.RidePreviewRequest
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideSavedPlacesStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════
 *  G O L D E N   J O U R N E Y   T R U T H   S U I T E
 *  ──────────────────────────────────────────────────────────────
 *  Suite canónica e inviolable de pruebas de verdad y convergencia.
 *  Ningún APK puede publicarse si esta suite falla.
 *
 *  Master Order Genesis §1, §2, §3, §4, §5.
 * ══════════════════════════════════════════════════════════════════════
 */
class GoldenJourneyTruthSuite {

    private val principalId = "pax-truth-vanguard-369"
    private lateinit var fakeBus: InMemoryTestRideCommandBus
    private lateinit var savedPlacesStore: RideSavedPlacesStore
    private lateinit var rideService: RideApplicationService
    private lateinit var rideCapability: RideRequestCapability

    @Before
    fun setUp() {
        val memoryPrefs = createMemoryPreferences()
        savedPlacesStore = RideSavedPlacesStore(memoryPrefs)
        val homePlace = RideSavedPlace(
            slot = "HOME",
            label = "Casa Vanguard",
            address = "Sabana Norte, San José, Costa Rica",
            latitude = 9.9380,
            longitude = -84.0950,
            providerId = "authoritative_user_saved",
        )
        savedPlacesStore.save(principalId, homePlace)

        fakeBus = InMemoryTestRideCommandBus()
        rideService = RideApplicationService(commandBus = fakeBus, savedPlacesStore = savedPlacesStore)
        rideCapability = RideRequestCapability(rideService)
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

    /**
     * TEST 1: Ride unknown destination returns PlaceNotFound, zero fake coordinates, zero ghost rides.
     * Prevents ever falling back to hardcoded GPS coordinates (9.9333, -84.0833).
     */
    @Test
    fun test1_unknownDestination_returnsNotFound_zeroFakeCoordinates_zeroGhostRides() = runBlocking {
        val unknownQuery = "Planeta Marte Sector 4 Nebulosa Omega"

        // 1. Service geocoding resolution directly returns NotFound
        val geocodingResult = rideService.resolveGeocodingQuery(unknownQuery)
        assertTrue(
            "Geocoding must strictly fail with NotFound for unknown places, never manufacture coordinates",
            geocodingResult is PlaceResolutionResult.NotFound,
        )

        // 2. Preview quote calculation returns null
        val previewRequest = RidePreviewRequest(
            principalId = principalId,
            pickupAlias = "HOME",
            destinationQuery = unknownQuery,
        )
        val quote = rideService.generatePreviewQuote(principalId, previewRequest)
        assertNull("Quote calculation must return null when destination cannot be resolved", quote)

        // 3. Capability execution returns failure with PLACE_RESOLUTION_FAILED
        val executionContext = AgentExecutionContext(
            principalId = principalId,
            contextGeneration = 1L,
            userConfirmed = false,
        )
        val result = rideCapability.execute(
            RideCapabilityInput(destinationQuery = unknownQuery, pickupAlias = "HOME"),
            executionContext,
        )

        assertTrue("Execution must return Failure", result is AgentResult.Failure)
        val failure = result as AgentResult.Failure
        assertEquals("PLACE_RESOLUTION_FAILED", failure.code)

        // 4. Assert zero ghost rides enqueued in command bus
        assertEquals("Zero commands must be enqueued for unresolvable destinations", 0, fakeBus.enqueuedCommands.size)
    }

    /**
     * TEST 2: Duplicate ASR audio callback triggers deterministic deduplication;
     * exactly one ride command is enqueued across 5 rapid identical frames.
     */
    @Test
    fun test2_duplicateAsrAudioCallback_triggersDeterministicDeduplication_exactlyOneCommand() = runBlocking {
        val previewRequest = RidePreviewRequest(
            principalId = principalId,
            pickupAlias = "HOME",
            destinationQuery = "Multiplaza Escazú",
        )
        val quote = rideService.generatePreviewQuote(principalId, previewRequest)
        assertNotNull("Quote for known place must be generated", quote)

        val stableIdempotencyKey = "asr_stream_dedup_tx_token_883311"
        val context = AgentExecutionContext(
            principalId = principalId,
            contextGeneration = 1L,
            userConfirmed = true,
            idempotencyKey = stableIdempotencyKey,
        )

        // Execute 5 rapid identical calls simulating duplicate speech frames/callbacks
        var firstRideId: String? = null
        for (i in 1..5) {
            val res = rideCapability.execute(
                RideCapabilityInput(destinationQuery = "Multiplaza Escazú", pickupAlias = "HOME"),
                context,
            )
            assertTrue("Duplicate call $i must succeed idempotently", res is AgentResult.Success)
            val data = (res as AgentResult.Success).data
            if (firstRideId == null) {
                firstRideId = data.rideId
            } else {
                assertEquals("Ride ID must remain identical across duplicate frames", firstRideId, data.rideId)
            }
        }

        // Must have enqueued exactly ONE command into the bus
        assertEquals("Exactly ONE ride command must be enqueued across 5 duplicate frames", 1, fakeBus.enqueuedCommands.size)
        assertEquals(stableIdempotencyKey, fakeBus.enqueuedCommands.first().idempotencyKey)
    }

    /**
     * TEST 3: Ride booking enqueues to RideCommandBus and reports Enqueued/Pending server confirmation;
     * never asserts synthetic server success locally.
     */
    @Test
    fun test3_rideBooking_reportsEnqueuedPending_neverAssertsSyntheticServerSuccessLocally() = runBlocking {
        val previewRequest = RidePreviewRequest(
            principalId = principalId,
            pickupAlias = "HOME",
            destinationQuery = "Multiplaza Escazú",
        )
        val quote = rideService.generatePreviewQuote(principalId, previewRequest)
        assertNotNull(quote)

        val idempotencyKey = "server_truth_tx_445566"
        val bookingResult = rideService.bookRide(principalId, quote!!, idempotencyKey)

        assertTrue(bookingResult is com.elysium369.meet.ride.application.RideBookingResult.Success)
        val success = bookingResult as com.elysium369.meet.ride.application.RideBookingResult.Success

        // Truth assertion: Message must state request is enqueued/sent awaiting server, NOT that ride is confirmed/accepted
        assertTrue("Message must indicate enqueued request awaiting server confirmation",
            success.message.contains("Solicitud enviada") || success.message.contains("Esperando confirmación")
        )
        assertFalse("Local message must not falsely claim driver is already assigned or ride accepted",
            success.message.contains("Conductor asignado") || success.message.contains("Viaje aceptado")
        )
    }

    /**
     * TEST 4: Secret/private UI fields (PIN, passwords, tokens, sensitive notes) reject voice agent
     * automated inspection or injection unless explicitly unlocked by owner biometric/explicit touch.
     */
    @Test
    fun test4_secretUiFields_rejectVoiceAgentAutomatedInspectionOrInjection() = runBlocking {
        val registry = AgentUiRegistry.default
        registry.clear()

        var injectedText: String? = null
        val secretControl = RegisteredAgentUiControl(
            snapshot = AgentUiControlSnapshot(
                id = AgentUiControlId("security.boarding_pin"),
                label = "PIN de abordaje secreto",
                focused = true,
                kind = AgentUiControlKind.TEXT_FIELD,
                role = AgentTextFieldRole.SECRET,
                sensitivity = AgentUiSensitivity.SECRET,
                bounds = Rect(0f, 0f, 200f, 60f),
            ),
            writeText = { text -> injectedText = text },
            readText = { "1234" },
        )
        registry.register(secretControl)

        val binder = VoiceFormBinder(registry)

        // Attempt voice binding on the secret field
        val partialResult = binder.bindPartial("mi pin es 9999", listOf(secretControl.snapshot))
        assertTrue("Voice binder must reject partial injection on SECRET field",
            partialResult is VoiceBindingResult.DeniedSecretField
        )

        val finalResult = binder.bindFinal("9999", listOf(secretControl.snapshot))
        assertTrue("Voice binder must reject final injection on SECRET field",
            finalResult is VoiceBindingResult.DeniedSecretField
        )

        // Verify that the secret field was NEVER written to by the voice agent
        assertNull("Secret field writeText must never be invoked by automated voice agent", injectedText)
    }

    /**
     * TEST 5: In-flight agent UI click on ambiguous semantic target prompts disambiguation dialog,
     * never clicks arbitrary sibling.
     */
    @Test
    fun test5_ambiguousSemanticTarget_promptsDisambiguation_neverClicksArbitrarySibling() = runBlocking {
        val button1 = AgentUiControlSnapshot(
            id = AgentUiControlId("action.save_changes"),
            label = "Guardar Cambios",
            kind = AgentUiControlKind.BUTTON,
            bounds = Rect(10f, 10f, 150f, 60f),
        )
        val button2 = AgentUiControlSnapshot(
            id = AgentUiControlId("action.save_draft"),
            label = "Guardar Borrador",
            kind = AgentUiControlKind.BUTTON,
            bounds = Rect(160f, 10f, 300f, 60f),
        )

        val resolver = UiTargetResolver()
        val result = resolver.resolve("Guardar", listOf(button1, button2))

        assertTrue(
            "Query with ambiguous candidates must return Ambiguous result, never arbitrarily pick one",
            result is UiTargetResolutionResult.Ambiguous,
        )

        val ambiguous = result as UiTargetResolutionResult.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
    }

    private class InMemoryTestRideCommandBus : RideCommandBus {
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
}
