package com.elysium369.meet.v6

import com.elysium369.meet.ai.*
import com.elysium369.meet.evidence.*
import com.elysium369.meet.simulation.SimulatedFault
import com.elysium369.meet.simulation.SimulatedVehicleState
import com.elysium369.meet.simulation.VirtualVehicleBusSimulator
import com.elysium369.meet.workshop.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class V6AdvancedSystemsSuiteTest {

    @Before
    fun setUp() {
        AiGatewayV2.resetAll()
    }

    // ----------------------------------------------------
    // 1. Evidence-Grounded Automotive RAG V2 Tests
    // ----------------------------------------------------
    @Test
    fun ragV2_exactMatchProducesIsolatedSafePrompt() {
        val docMeta = AutomotiveDocumentMetadata(
            sourceId = "MAN-COROLLA-2021",
            revision = "REV-2021.04",
            documentHashSha256 = "abc123hash",
            manufacturer = "Toyota",
            model = "Corolla",
            yearMin = 2019,
            yearMax = 2023,
            market = "USDM",
            engineCode = "2ZR-FXE",
            sourceAuthority = "OEM_WORKSHOP_MANUAL",
        )
        val docContent = "Cylinder head bolts: Step 1: 49 N*m (500 kgf*cm, 36 ft*lbf). Step 2: Turn 90 degrees."

        val query = GroundedVehicleQuery(
            make = "Toyota",
            model = "Corolla",
            year = 2021,
            engineCode = "2ZR-FXE",
            queryTopic = "Cylinder head torque",
        )

        val outcome = EvidenceGroundedAutomotiveRagV2.matchAndRetrieve(query, listOf(Pair(docMeta, docContent)))
        assertTrue(outcome is RagRetrievalOutcome.VerifiedGroundedContext)
        val verified = outcome as RagRetrievalOutcome.VerifiedGroundedContext
        assertTrue(verified.safePromptData.contains("unexecutable=\"true\""))
        assertTrue(verified.safePromptData.contains("Cylinder head bolts"))
    }

    @Test
    fun ragV2_wrongEngineReturnsNotFoundAndNeverInventsTorque() {
        val docMeta = AutomotiveDocumentMetadata(
            sourceId = "MAN-GOLF-2019",
            revision = "REV-2019.01",
            documentHashSha256 = "golf123hash",
            manufacturer = "Volkswagen",
            model = "Golf",
            yearMin = 2018,
            yearMax = 2022,
            market = "EUDM",
            engineCode = "EA888",
            sourceAuthority = "OEM_WORKSHOP_MANUAL",
        )
        val docContent = "Spark plug torque: 30 Nm."

        val query = GroundedVehicleQuery(
            make = "Volkswagen",
            model = "Golf",
            year = 2019,
            engineCode = "EA211", // Different engine!
            queryTopic = "Spark plug torque",
        )

        val outcome = EvidenceGroundedAutomotiveRagV2.matchAndRetrieve(query, listOf(Pair(docMeta, docContent)))
        assertTrue("Mismatched engine must fail closed", outcome is RagRetrievalOutcome.ApplicableSourceNotFound)
    }

    // ----------------------------------------------------
    // 2. AI Gateway V2 Bulkheads and Circuit Breakers
    // ----------------------------------------------------
    @Test
    fun aiGatewayV2_taskSpecificCircuitBreakers() {
        assertTrue(AiGatewayV2.canExecute(AiTaskClass.LEGAL_TRIAGE))

        // Record 3 consecutive 5xx/timeout failures on LEGAL_TRIAGE
        AiGatewayV2.recordFailure(AiTaskClass.LEGAL_TRIAGE, isTimeoutOr5xx = true)
        AiGatewayV2.recordFailure(AiTaskClass.LEGAL_TRIAGE, isTimeoutOr5xx = true)
        AiGatewayV2.recordFailure(AiTaskClass.LEGAL_TRIAGE, isTimeoutOr5xx = true)

        // Circuit must open for LEGAL_TRIAGE
        assertFalse("LEGAL_TRIAGE circuit must be OPEN", AiGatewayV2.canExecute(AiTaskClass.LEGAL_TRIAGE))

        // But DTC_SUMMARY bulkhead must remain unaffected and CLOSED!
        assertTrue("DTC_SUMMARY bulkhead must remain isolated and CLOSED", AiGatewayV2.canExecute(AiTaskClass.DTC_SUMMARY))
    }

    // ----------------------------------------------------
    // 3. Workshop Voice Safety V2 Interlocks
    // ----------------------------------------------------
    @Test
    fun voiceSafety_readOnlyAllowedDirectly() {
        val preconditions = VoiceExecutionPreconditions(
            vehicleSpeedKmh = 60f,
            engineRpm = 2200f,
            isIgnitionOn = true,
            isEngineRunning = true,
        )

        val auth = WorkshopVoiceSafetyV2.evaluateAuthorization(
            command = WorkshopVoiceCommandType.READ_RPM,
            preconditions = preconditions,
            voiceIntentRecognized = true,
            onScreenUserConfirmed = false,
        )

        assertTrue(auth is VoiceCommandAuthorization.Authorized)
    }

    @Test
    fun voiceSafety_destructiveCommandBlockedWhileVehicleMoving() {
        val preconditions = VoiceExecutionPreconditions(
            vehicleSpeedKmh = 15f, // Moving!
            engineRpm = 1000f,
            isIgnitionOn = true,
            isEngineRunning = true,
        )

        val auth = WorkshopVoiceSafetyV2.evaluateAuthorization(
            command = WorkshopVoiceCommandType.CLEAR_DTC,
            preconditions = preconditions,
            voiceIntentRecognized = true,
            onScreenUserConfirmed = true,
        )

        assertTrue(auth is VoiceCommandAuthorization.Blocked)
        val blocked = auth as VoiceCommandAuthorization.Blocked
        assertTrue(blocked.violationReason.contains("Vehicle is moving"))
    }

    // ----------------------------------------------------
    // 4. Vehicle Evidence Passport V2 Crypto-Agility
    // ----------------------------------------------------
    @Test
    fun evidencePassport_chainIntegrityVerification() {
        val env1 = SignatureEnvelope(CryptoAlgorithm.ECDSA_P256, 1, "key-android-hw-1", "30450221...", "attest-sha")
        val rec1 = VehicleEvidencePassportV2.buildNextRecord("rec-1", "veh-100", "merkle-root-1", null, 1000L, env1)

        val env2 = SignatureEnvelope(CryptoAlgorithm.ML_DSA_65, 1, "key-pqc-postquantum-2", "mldsa-sig-hex...", null)
        val rec2 = VehicleEvidencePassportV2.buildNextRecord("rec-2", "veh-100", "merkle-root-2", rec1, 2000L, env2)

        assertTrue(VehicleEvidencePassportV2.verifyChainIntegrity(listOf(rec1, rec2)))

        // Tamper rec1 hash in rec2
        val tamperedRec2 = rec2.copy(previousPassportHash = "corrupted-hash")
        assertFalse(VehicleEvidencePassportV2.verifyChainIntegrity(listOf(rec1, tamperedRec2)))
    }

    // ----------------------------------------------------
    // 5. Virtual Vehicle Simulation Laboratory
    // ----------------------------------------------------
    @Test
    fun vehicleSimulator_deterministicResponseAndFaultInjection() {
        val sim = VirtualVehicleBusSimulator(
            state = SimulatedVehicleState(engineRpm = 2000f, vehicleSpeedKmh = 50f)
        )

        // Normal response
        val atz = sim.handleAsciiCommand("ATZ")
        assertTrue(atz.contains("ELM327"))

        val rpm = sim.handleAsciiCommand("010C")
        assertTrue(rpm.startsWith("41 0C"))

        val dtc = sim.handleAsciiCommand("03")
        assertTrue(dtc.contains("43 02 03 00 01 71")) // P0300, P0171

        // Inject CAN bus off fault
        sim.activeFault = SimulatedFault.CAN_BUS_OFF_ERROR
        val faulted = sim.handleAsciiCommand("010C")
        assertTrue(faulted.contains("CAN ERROR"))
    }
}
