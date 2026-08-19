package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.SimulatedTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSupervisorAndObdStabilityTest {

    @Test
    fun testKnownGoodAdapterStoreRecordAndFastPath() {
        val fingerprint = "AA:BB:CC:DD:EE:FF"
        KnownGoodAdapterStore.recordSuccess(
            fingerprint = fingerprint,
            transportType = TransportType.BLUETOOTH_CLASSIC,
            connectMethod = ConnectMethod.INSECURE_SPP,
            protocol = "6",
            connectDurationMs = 320L,
            rfcommChannel = 1,
            preferredInitRecipe = "ATZ;ATE0;ATH1;ATSP6",
        )

        val profile = KnownGoodAdapterStore.getProfile(fingerprint)
        assertNotNull(profile)
        assertEquals(ConnectMethod.INSECURE_SPP, profile?.preferredConnectMethod)
        assertEquals("6", profile?.preferredProtocol)
        assertEquals(1, profile?.rfcommChannel)
        assertEquals("ATZ;ATE0;ATH1;ATSP6", profile?.preferredInitRecipe)
        assertEquals(0, profile?.failureCount)
    }

    @Test
    fun testProtocolTimeoutPolicies() {
        val canTimeoutFast = ProtocolTimeoutPolicy.getTimeoutMs(ObdProtocol.CAN_11BIT_500K, ObdOperationType.LIVE_PID_FAST, isClone = false)
        assertTrue(canTimeoutFast in 100L..350L)

        val legacyTimeoutFast = ProtocolTimeoutPolicy.getTimeoutMs(ObdProtocol.ISO9141, ObdOperationType.LIVE_PID_FAST, isClone = false)
        assertTrue(legacyTimeoutFast >= 500L)

        val clearVerifyTimeout = ProtocolTimeoutPolicy.getTimeoutMs(ObdProtocol.CAN_11BIT_500K, ObdOperationType.DTC_CLEAR_VERIFICATION, isClone = false)
        assertTrue(clearVerifyTimeout <= 1000L)
    }

    @Test
    fun testConnectionSupervisorLifecycleAndHealth() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val supervisor = ConnectionSupervisor(scope)

        val transport = supervisor.switchTransport("00:11:22:33:44:55") {
            SimulatedTransport()
        }
        assertNotNull(transport)

        supervisor.connectWithFastPath("SIMULATED_ADAPTER") {
            it.connect()
        }

        supervisor.updateProtocolHealth(ProtocolHealth.LOCKED, EcuHealth.RESPONSIVE)
        assertTrue(supervisor.health.value.isFullyFunctional)
        assertEquals(TransportHealth.CONNECTED, supervisor.health.value.transport)
        assertEquals(AdapterHealth.SYNCHRONIZED, supervisor.health.value.adapter)

        supervisor.recordExchangeOutcome(isSuccess = true, latencyMs = 25L)
        assertEquals(0, supervisor.health.value.consecutiveTimeouts)

        supervisor.disconnect()
        assertEquals(TransportHealth.DISCONNECTED, supervisor.health.value.transport)
    }

    @Test
    fun testDiagnosticScanModeClearVerifyEnum() {
        assertEquals(DiagnosticScanMode.CLEAR_VERIFY, DiagnosticScanMode.valueOf("CLEAR_VERIFY"))
        val plan = SaeObdDiagnosticStrategy.compileDtcPlan(DiagnosticScanMode.CLEAR_VERIFY)
        assertEquals(listOf("03", "07"), plan.primaryRequests)
    }

    @Test
    fun testClearVerifyOnlyPlansTargetEcus() {
        val confirmedModules = listOf(
            NetworkModule(id = "7E0", name = "Engine Control Module (ECM)", isAlive = true, responseId = "7E8"),
            NetworkModule(id = "7E1", name = "Transmission Control Module (TCM)", isAlive = true, responseId = "7E9"),
            NetworkModule(id = "7E2", name = "Anti-Lock Brake System (ABS)", isAlive = true, responseId = "7EA"),
        )

        val target = ClearVerificationTarget(
            findingId = "finding_p0301",
            vehicleId = "veh_123",
            findingKey = DiagnosticFindingKey(
                vehicleId = "veh_123",
                namespace = DiagnosticNamespace.SAE_OBD,
                moduleIdentity = "7E0",
                rawDtcIdentity = "P0301",
                displayCode = "P0301",
            ),
            requiredSemantics = setOf(DiagnosticSemantic.SAE_ACTIVE_DTC),
            sourceService = "03",
        )

        val verificationPlan = ClearVerificationPlan(
            requestedAtMs = System.currentTimeMillis(),
            targets = listOf(target),
            preClearReport = null,
        )

        val compiledTargets = DiagnosticScanPlanCompiler.compile(
            mode = DiagnosticScanMode.CLEAR_VERIFY,
            confirmedModules = confirmedModules,
            discoveryCandidates = mapOf("7DF" to "Functional", "7E0" to "ECM", "7E1" to "TCM", "7E2" to "ABS"),
            clearVerificationPlan = verificationPlan,
        )

        // Only ECM 7E0 should be planned, NOT TCM 7E1 or ABS 7E2!
        assertEquals(1, compiledTargets.size)
        assertEquals("7E0", compiledTargets.first().requestAddress)
        assertEquals("Engine Control Module (ECM)", compiledTargets.first().moduleName)
    }
}
