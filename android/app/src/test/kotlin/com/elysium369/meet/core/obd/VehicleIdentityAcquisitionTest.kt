package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleIdentityAcquisitionTest {
    private val udsEndpoint = EcuEndpoint(
        busId = "CAN0",
        networkType = DiagnosticTransport.CAN,
        addressingMode = DiagnosticAddressingMode.PHYSICAL,
        requestAddress = "7E0",
        responseAddress = "7E8",
        discoveryProvenance = "TEST_CONFIRMED",
    )

    @Test fun mode09VinSupportedPath() {
        val plan = VinStrategyCompiler.compile(
            VinCapabilityContext(DiagnosticTransport.CAN, DiagnosticApplicationProtocol.SAE_OBD, true),
        )
        assertEquals(listOf("0902"), plan.probes.map(VinProbe::command))
    }

    @Test fun mode09UnsupportedDoesNotLoop() {
        val plan = VinStrategyCompiler.compile(
            VinCapabilityContext(DiagnosticTransport.CAN, DiagnosticApplicationProtocol.SAE_OBD, false),
        )
        assertTrue(plan.probes.isEmpty())
        assertEquals(VehicleIdentityResultCode.NOT_SUPPORTED, plan.unavailableReason)
    }

    @Test fun udsF190Vin() {
        val plan = VinStrategyCompiler.compile(
            VinCapabilityContext(DiagnosticTransport.CAN, DiagnosticApplicationProtocol.UDS, knownUdsEndpoint = udsEndpoint),
        )
        assertEquals(VinStrategy.UDS_F190, plan.probes.single().strategy)
        assertEquals("22F190", plan.probes.single().command)
    }

    @Test fun kwpSpecificVinCommandRequiresRecipe() {
        val noRecipe = VinStrategyCompiler.compile(
            VinCapabilityContext(DiagnosticTransport.K_LINE, DiagnosticApplicationProtocol.KWP2000),
        )
        assertTrue(noRecipe.probes.isEmpty())
        val sourced = VinStrategyCompiler.compile(
            VinCapabilityContext(
                DiagnosticTransport.K_LINE,
                DiagnosticApplicationProtocol.KWP2000,
                authorizedKwpRecipeId = "recipe-cr-v1",
                authorizedKwpCommands = listOf("1A90"),
            ),
        )
        assertEquals("recipe-cr-v1", sourced.probes.single().recipeId)
    }

    @Test fun invalidVinNeverBindsVehicle() {
        val result = VinConsensusEvaluator.evaluate(listOf(observation("bad", "INVALID", VinResponseOutcome.INVALID_RESPONSE)))
        assertEquals(VehicleIdentityResultCode.INVALID_RESPONSE, result.resultCode)
        assertNull(result.verifiedVin)
    }

    @Test fun multipleMatchingEcuVinCreatesConsensus() {
        val vin = "1HGCM82633A004352"
        val result = VinConsensusEvaluator.evaluate(listOf(observation("a", vin), observation("b", vin)))
        assertEquals(VehicleIdentityResultCode.MULTI_ECU_CONSENSUS, result.resultCode)
        assertEquals(vin, result.verifiedVin)
    }

    @Test fun conflictingVinCreatesConflict() {
        val result = VinConsensusEvaluator.evaluate(
            listOf(observation("a", "1HGCM82633A004352"), observation("b", "1M8GDM9AXKP042788")),
        )
        assertEquals(VehicleIdentityResultCode.CONFLICT_DETECTED, result.resultCode)
        assertNull(result.verifiedVin)
    }

    private fun observation(
        id: String,
        vin: String,
        outcome: VinResponseOutcome = VinResponseOutcome.VERIFIED,
    ) = VinObservation(
        identityObservationId = id,
        diagnosticSessionId = "session",
        vehicleBindingId = null,
        strategy = VinStrategy.UDS_F190,
        transport = DiagnosticTransport.CAN,
        protocol = DiagnosticApplicationProtocol.UDS,
        requestAddress = "7E0",
        responseAddress = "7E8",
        ecuIdentity = "ECM",
        startedMonotonicMs = 1,
        completedMonotonicMs = 2,
        responseOutcome = outcome,
        rawResponseHash = "0".repeat(64),
        parserVersion = "1",
        normalizedVin = vin,
        vinHash = if (vin.length == 17) VinObservation.verifiedVinHash(vin) else null,
        vinLength = vin.length,
    )
}
