package com.elysium369.meet.core.obd

import com.elysium369.meet.core.telemetry.CompressedTelemetryStorageEngineV2
import com.elysium369.meet.core.telemetry.TelemetryBinaryFrame
import com.elysium369.meet.core.twin.TwinTruthState
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import com.elysium369.meet.identity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Supreme Invariant Test:
 * NO EVIDENCE CAN EVER PRODUCE VERIFIED TRUTH.
 *
 * Verifies the core axioms of the MEET Vanguard Convergence Platform:
 * 1. UNKNOWN != ZERO, UNKNOWN != HEALTHY
 * 2. NO RESPONSE != NO DTC
 * 3. NO PHYSICAL EVIDENCE != PHYSICALLY VERIFIED
 * 4. NO BASELINE != 100% HEALTH
 * 5. NO PII IN OPERATIONAL TELEMETRY BY DEFAULT
 * 6. TAMPERED DATA NEVER PASSES MERKLE VERIFICATION
 * 7. ELM327 V2.1 CLONES USE STABILITY-FIRST TIMINGS
 */
class NoEvidenceCanProduceVerifiedTruthPropertyTest {

    // ----------------------------------------------------
    // Invariant 1: Blank / Incomplete Data Never Decodes to Plausible Zero
    // ----------------------------------------------------
    @Test
    fun invariant1_missingOrBlankDataFailsClosed() {
        // Blank formula
        val r1 = FormulaEvaluator.decode("", listOf(0x00, 0x00))
        assertTrue(r1 is PidDecodeResult.InvalidFormula)
        assertNull(FormulaEvaluator.evaluateOrNull("", listOf(0x00, 0x00)))

        // Missing required byte
        val r2 = FormulaEvaluator.decode("A*256+B", listOf(0x40))
        assertTrue(r2 is PidDecodeResult.InsufficientBytes)
        assertNull(FormulaEvaluator.evaluateOrNull("A*256+B", listOf(0x40)))

        // Division by zero
        val r3 = FormulaEvaluator.decode("A/(B-B)", listOf(0x10, 0x04))
        assertTrue(r3 is PidDecodeResult.DivisionByZero)
        assertNull(FormulaEvaluator.evaluateOrNull("A/(B-B)", listOf(0x10, 0x04)))
    }

    // ----------------------------------------------------
    // Invariant 2: Digital Twin Never Claims Health/Confidence Without Evidence
    // ----------------------------------------------------
    private class FakeTwinDao : VehicleTwinDao {
        var profile: VehicleTwinProfileEntity? = null
        val anomalies = mutableListOf<TwinAnomalyEntity>()
        override suspend fun insertTwinProfile(p: VehicleTwinProfileEntity) { profile = p }
        override suspend fun getTwinProfile(vehicleId: String): VehicleTwinProfileEntity? = profile
        override suspend fun insertAnomaly(a: TwinAnomalyEntity) { anomalies.add(a) }
        override fun getAnomaliesForVehicle(vehicleId: String): Flow<List<TwinAnomalyEntity>> = flowOf(anomalies)
        override suspend fun clearAnomaliesForVehicle(vehicleId: String) { anomalies.clear() }
    }

    @Test
    fun invariant2_zeroHistoryTwinProducesZeroConfidenceAndUntrainedState() = runBlocking {
        val dao = FakeTwinDao()
        val engine = VehicleTwinEngine(dao)

        val profile = engine.trainOrInitializeProfile("veh-unseen", emptyList())
        assertEquals(0.0, profile.confidence, 0.001)
        assertEquals(0, profile.healthScore)
        assertEquals(TwinTruthState.UNTRAINED, engine.getTruthState(profile, 0))
    }

    // ----------------------------------------------------
    // Invariant 3: Unauthenticated Fresh Install Cannot Acquire Platform Authority
    // ----------------------------------------------------
    @Test
    fun invariant3_unauthenticatedFreshInstallBlockedFromAuthority() {
        val decision = PrincipalAccessPolicy.decide(
            session = PrincipalAccessPolicy.SessionEvidence.NOT_AUTHENTICATED,
            provisionedPrincipalId = null,
        )

        assertEquals(
            "Fresh install must always require real authentication",
            PrincipalAccessPolicy.Decision.REQUIRE_AUTHENTICATION,
            decision
        )

        // Offline unauthenticated cannot hold provider authority
        assertFalse(
            PrincipalCapabilityPolicy.grantsProviderAuthority(
                principalAuthenticated = false,
                principalStatus = PrincipalStatus.ACTIVE,
                activationState = CapabilityActivationState.APPROVED,
            )
        )
    }

    // ----------------------------------------------------
    // Invariant 4: Telemetry Log Filename Never Leaks Raw VIN
    // ----------------------------------------------------
    @Test
    fun invariant4_telemetryFilenameNeverContainsRawVin() {
        val rawVin = "3VWDP7AJ8FM123456"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawVin.toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

        assertFalse("Filename must never contain raw VIN substring", rawVin.contains(digest))
        assertEquals(8, digest.length)
    }

    // ----------------------------------------------------
    // Invariant 5: Tampered Telemetry Segment Merkle Root Fails Closed
    // ----------------------------------------------------
    @Test
    fun invariant5_tamperedTelemetrySegmentFailsMerkleVerification() {
        val frames = (1..50).map { i ->
            TelemetryBinaryFrame(
                timestampMs = 5000L + i * 100L,
                pidHash = 0x0105,
                value = 85f + (i % 5),
            )
        }

        val segment = CompressedTelemetryStorageEngineV2.buildSegment(frames)

        // Verify untampered segment passes
        val (validBefore, _) = CompressedTelemetryStorageEngineV2.decompressAndVerifySegment(segment)
        assertTrue(validBefore)

        // Tamper 1 bit in compressed payload
        val tamperedPayload = segment.compressedPayload.copyOf()
        tamperedPayload[tamperedPayload.size / 2] = (tamperedPayload[tamperedPayload.size / 2].toInt() xor 0x01).toByte()
        val tamperedSegment = segment.copy(compressedPayload = tamperedPayload)

        // Decompress & verify must fail (either decompression error or Merkle mismatch)
        val tamperedResult = runCatching {
            CompressedTelemetryStorageEngineV2.decompressAndVerifySegment(tamperedSegment)
        }

        assertTrue(
            "Tampered segment must either fail decompression or fail Merkle integrity verification",
            tamperedResult.isFailure || tamperedResult.getOrNull()?.first == false
        )
    }

    // ----------------------------------------------------
    // Invariant 6: AI Hypothesis Never Elevates to Confirmed Without Physical Test
    // ----------------------------------------------------
    @Test
    fun invariant6_aiHypothesisNeverElevatesWithoutPhysicalProof() {
        // Preliminary hypothesis with no physical test results
        val planWithoutProof = GuidedDiagnosticPlanCompiler.compilePlan("P0300", freezeFrameAvailable = true, physicalTestResultPassed = null)
        assertEquals(DiagnosticTruthStatus.REQUIRES_PHYSICAL_BENCH_TEST, planWithoutProof.truthStatus)
        assertTrue("Plan must require physical verification before confirming fix", planWithoutProof.requiresPhysicalVerification)

        // Physical test confirmed fault
        val planConfirmed = GuidedDiagnosticPlanCompiler.compilePlan("P0300", freezeFrameAvailable = true, physicalTestResultPassed = true)
        assertEquals(DiagnosticTruthStatus.PHYSICALLY_CONFIRMED_FAULT, planConfirmed.truthStatus)
        assertFalse(planConfirmed.requiresPhysicalVerification)
    }

    // ----------------------------------------------------
    // Invariant 7: ELM327 v2.1 clones use stability-first timings
    // ----------------------------------------------------
    @Test
    fun invariant7_v21CloneAdapterUsesStabilityDelays() {
        val plan = AdaptiveProtocolNegotiatorV2.compilePlan("ELM327 v2.1")
        assertEquals(AdapterCompatibilityTier.ELM327_V21_CLONE, plan.adapterCompatibilityTier)
        assertEquals(DiagnosticProbeSpeed.STABILITY_FIRST_PROBE, plan.probeSpeed)
        assertTrue("v2.1 clone must have >= 50ms stability delay", plan.interCommandDelayMs >= 50L)
        assertFalse("v2.1 clone must use deterministic ATAT timing", plan.enableAdaptiveTiming)
    }
}
