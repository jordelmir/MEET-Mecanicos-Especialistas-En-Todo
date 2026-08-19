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
        )

        val profile = KnownGoodAdapterStore.getProfile(fingerprint)
        assertNotNull(profile)
        assertEquals(ConnectMethod.INSECURE_SPP, profile?.preferredConnectMethod)
        assertEquals("6", profile?.preferredProtocol)
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
}
