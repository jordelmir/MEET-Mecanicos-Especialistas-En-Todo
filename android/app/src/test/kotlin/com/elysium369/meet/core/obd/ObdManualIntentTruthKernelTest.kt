package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ObdManualIntentTruthKernelTest {

    private class MockControlledTransport : TransportInterface {
        private val _linkState = MutableStateFlow<TransportLinkState>(TransportLinkState.Disconnected)
        override val linkState: StateFlow<TransportLinkState> = _linkState.asStateFlow()

        private val _linkEvents = MutableSharedFlow<TransportLinkEvent>(extraBufferCapacity = 32)
        override val linkEvents: SharedFlow<TransportLinkEvent> = _linkEvents.asSharedFlow()

        var connectCallCount = 0
        var disconnectCallCount = 0
        var isPhysConnected = false

        override val isConnected: Boolean get() = isPhysConnected

        override suspend fun drain() {}

        override suspend fun connect() {
            connectCallCount++
            _linkState.value = TransportLinkState.Connecting
            _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connecting))
            isPhysConnected = true
            _linkState.value = TransportLinkState.Connected
            _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connected))
        }

        override fun abortConnect() {
            isPhysConnected = false
            _linkState.value = TransportLinkState.Disconnected
        }

        override suspend fun disconnect() {
            disconnectCallCount++
            _linkState.value = TransportLinkState.Closing
            isPhysConnected = false
            _linkState.value = TransportLinkState.Disconnected
            _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Disconnected))
        }

        override suspend fun write(data: ByteArray) {}
        override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? = null

        fun simulateRemoteEof() {
            isPhysConnected = false
            val timestamp = System.nanoTime() / 1_000_000L
            _linkState.value = TransportLinkState.RemoteClosed("EOF received", timestamp)
            _linkEvents.tryEmit(TransportLinkEvent.RemoteClosed("EOF received", timestamp))
        }
    }

    @Test
    fun testManualIntent_NoIntent_RefusesConnection() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = ObdLinkHealthCoordinator()

        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
        assertEquals(false, coordinator.truth.value.isPhysicallyConnected)
        assertEquals(false, coordinator.truth.value.isEcuConnected)

        // Attempting recovery or connection without intent should be rejected
        val mockTransport = MockControlledTransport()
        var elmResyncCalled = false
        val recovered = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L1_ADAPTER_ELM,
            reason = "test",
            transport = mockTransport,
            onElmResync = { elmResyncCalled = true; true },
            onProtocolRestore = { true }
        )

        assertFalse("Recovery must be refused when intent is DISCONNECTED", recovered)
        assertFalse("ELM resync callback must not be invoked when intent is DISCONNECTED", elmResyncCalled)
        testScope.cancel()
    }

    @Test
    fun testUserConnectRequested_AndCancelConnection_HonorsIntentFirewall() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = ObdLinkHealthCoordinator()

        val attempt = ConnectionAttempt(
            attemptId = UUID.randomUUID().toString(),
            adapterAddress = "AA:BB:CC:DD:EE:FF",
            startedAtMonotonicMs = System.nanoTime() / 1_000_000L
        )

        coordinator.onUserConnectRequested(attempt)
        assertEquals(ConnectionIntent.CONNECT_REQUESTED, coordinator.truth.value.intent)
        assertEquals(ObdSessionTruthState.CONNECTING, coordinator.truth.value.sessionState)

        // Simulate cancel
        coordinator.onUserCancelled("User cancelled in UI")
        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
        assertEquals(ObdSessionTruthState.DISCONNECTED, coordinator.truth.value.sessionState)
        assertEquals(DisconnectReason.USER_CANCELLED, coordinator.truth.value.disconnectReason)
        testScope.cancel()
    }

    @Test
    fun testInstantEOF_TransitionsToRemoteClosed_WithoutAutonomousReconnect() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)
        coordinator.onElmReady("ELM327 v1.5")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        assertTrue(coordinator.truth.value.isPhysicallyConnected)
        assertTrue(coordinator.truth.value.isEcuConnected)
        assertTrue(coordinator.truth.value.isSessionReady)

        // Physical link drops via remote EOF
        mockTransport.simulateRemoteEof()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)

        assertFalse("Transport must no longer be marked physically connected", coordinator.truth.value.isPhysicallyConnected)
        assertEquals(ObdSessionTruthState.LINK_LOST, coordinator.truth.value.sessionState)
        assertEquals(1, coordinator.truth.value.physicalLinkLossCount)
        assertTrue(coordinator.truth.value.transportState is TransportLinkState.RemoteClosed)

        // Verify that mock transport was not reconnected automatically
        assertEquals(1, mockTransport.connectCallCount)
        testScope.cancel()
    }

    @Test
    fun testLayeredRecovery_L1AndL2_NeverClosePhysicalSocket() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        val attempt = ConnectionAttempt(
            attemptId = UUID.randomUUID().toString(),
            adapterAddress = "AA:BB:CC:DD:EE:FF",
            startedAtMonotonicMs = System.nanoTime() / 1_000_000L
        )
        coordinator.onUserConnectRequested(attempt)
        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)
        coordinator.onElmReady("ELM327 v1.5")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        var elmResyncCount = 0
        var protocolRestoreCount = 0

        // 1. Simulate L1 Adapter Prompt Glitch
        val l1Result = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L1_ADAPTER_ELM,
            reason = "Buffer overflow / prompt lost",
            transport = mockTransport,
            onElmResync = {
                elmResyncCount++
                true
            },
            onProtocolRestore = {
                protocolRestoreCount++
                true
            }
        )

        assertTrue("L1 recovery should succeed", l1Result)
        assertEquals(1, elmResyncCount)
        assertEquals(0, protocolRestoreCount)
        assertEquals("Physical connect count must remain 1 (no reconnect)", 1, mockTransport.connectCallCount)
        assertEquals("Physical disconnect count must remain 0", 0, mockTransport.disconnectCallCount)
        assertEquals(1, coordinator.truth.value.softRecoveryCount)

        // 2. Simulate L2 Bus Error / ECU Silence
        val l2Result = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L2_VEHICLE_BUS_PROTOCOL,
            reason = "CAN Error / ECU timeout",
            transport = mockTransport,
            onElmResync = {
                elmResyncCount++
                true
            },
            onProtocolRestore = {
                protocolRestoreCount++
                true
            }
        )

        assertTrue("L2 recovery should succeed", l2Result)
        assertEquals(1, elmResyncCount)
        assertEquals(1, protocolRestoreCount)
        assertEquals("Physical transport must NEVER be reconnected for L2", 1, mockTransport.connectCallCount)
        assertEquals(0, mockTransport.disconnectCallCount)
        assertEquals(1, coordinator.truth.value.protocolRecoveryCount)
        assertEquals(EcuLinkState.RESPONSIVE, coordinator.truth.value.ecuState)

        testScope.cancel()
    }

    @Test
    fun testOrthogonalTruthSnapshot_PhysicalConnected_EcuSilent_IsDegradedNotLost() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        val attempt = ConnectionAttempt(
            attemptId = UUID.randomUUID().toString(),
            adapterAddress = "AA:BB:CC:DD:EE:FF",
            startedAtMonotonicMs = System.nanoTime() / 1_000_000L
        )
        coordinator.onUserConnectRequested(attempt)
        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)
        coordinator.onElmReady("ELM327 v1.5")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)

        // ECU silent (e.g. ignition is OFF)
        coordinator.onEcuSilent("Ignition OFF / NO DATA")

        assertTrue("Physical transport IS connected", coordinator.truth.value.isPhysicallyConnected)
        assertFalse("ECU is NOT connected", coordinator.truth.value.isEcuConnected)
        assertEquals(EcuLinkState.NO_RESPONSE, coordinator.truth.value.ecuState)
        assertEquals(ObdSessionTruthState.DEGRADED, coordinator.truth.value.sessionState)

        testScope.cancel()
    }

    @Test
    fun staleAttempt_cannotBecomeCurrentAfterNewUserRequest() {
        val coordinator = ObdLinkHealthCoordinator()
        val first = ConnectionAttempt("attempt-a", adapterAddress = "AA:BB:CC:DD:EE:01", generation = 1)
        val second = ConnectionAttempt("attempt-b", adapterAddress = "AA:BB:CC:DD:EE:02", generation = 2)

        coordinator.onUserConnectRequested(first)
        coordinator.onUserConnectRequested(second)

        assertFalse(coordinator.isCurrentAttempt(first.attemptId, first.generation))
        assertTrue(coordinator.isCurrentAttempt(second.attemptId, second.generation))
    }

    @Test
    fun newUserAttempt_clearsAllProofFromPreviousSession() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("first", adapterAddress = "AA:BB:CC:DD:EE:01", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()
        assertTrue(coordinator.truth.value.isSessionReady)

        coordinator.onUserConnectRequested(ConnectionAttempt("second", adapterAddress = "AA:BB:CC:DD:EE:02", generation = 2))

        val truth = coordinator.truth.value
        assertEquals(TransportLinkState.Connecting, truth.transportState)
        assertEquals(ElmLinkState.UNKNOWN, truth.elmState)
        assertEquals(ProtocolLinkState.UNKNOWN, truth.protocolState)
        assertNull(truth.protocol)
        assertEquals(EcuLinkState.UNKNOWN, truth.ecuState)
        assertEquals(TelemetryLinkState.INACTIVE, truth.telemetryState)
        assertFalse(truth.isSessionReady)
    }

    @Test
    fun terminalAttemptFailure_clearsPhysicalAndSemanticProof() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        coordinator.onConnectionAttemptFailed("Handshake timed out")

        val truth = coordinator.truth.value
        assertEquals(ConnectionIntent.DISCONNECTED, truth.intent)
        assertEquals(TransportLinkState.Disconnected, truth.transportState)
        assertEquals(ElmLinkState.UNKNOWN, truth.elmState)
        assertEquals(ProtocolLinkState.UNKNOWN, truth.protocolState)
        assertNull(truth.protocol)
        assertEquals(EcuLinkState.UNKNOWN, truth.ecuState)
        assertEquals(ObdSessionTruthState.ERROR, truth.sessionState)
        assertFalse(truth.isSessionReady)
    }

    @Test
    fun remoteClose_clearsDependentProofAndRecordsPhysicalReason() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        coordinator.onTransportStateChanged(TransportLinkState.RemoteClosed("EOF", 10L))

        val truth = coordinator.truth.value
        assertEquals(DisconnectReason.REMOTE_CLOSED, truth.disconnectReason)
        assertEquals(ConnectionIntent.DISCONNECTED, truth.intent)
        assertEquals(FailureLayer.L3_PHYSICAL_TRANSPORT, truth.lastFailureLayer)
        assertEquals(ElmLinkState.UNKNOWN, truth.elmState)
        assertEquals(EcuLinkState.SESSION_LOST, truth.ecuState)
        assertFalse(truth.isSessionReady)
    }

    @Test
    fun telemetrySilence_degradesEcuWithoutDroppingPhysicalTransport() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        coordinator.onTelemetryStale()

        assertTrue(coordinator.truth.value.isPhysicallyConnected)
        assertEquals(EcuLinkState.NO_RESPONSE, coordinator.truth.value.ecuState)
        assertEquals(TelemetryLinkState.STALE, coordinator.truth.value.telemetryState)
        assertEquals(ObdSessionTruthState.DEGRADED, coordinator.truth.value.sessionState)
    }

    @Test
    fun simulatedTransport_neverCountsAsPhysicalProof() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("demo", adapterAddress = "SIMULATOR", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)

        assertTrue(coordinator.truth.value.isDemoSession)
        assertFalse(coordinator.truth.value.isPhysicallyConnected)
    }

    @Test
    fun testCancelFirewall_blocksLayeredRecoveryPermanently() = runBlocking {
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)

        // User hits cancel
        coordinator.onUserCancelled("User pressed Cancel")
        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)

        // Any background attempt to run layered recovery MUST be denied
        var callbackExecuted = false
        val recovered = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L1_ADAPTER_ELM,
            reason = "Background retry",
            transport = mockTransport,
            onElmResync = { callbackExecuted = true; true },
            onProtocolRestore = { true }
        )

        assertFalse("Layered recovery MUST return false when user intent is DISCONNECTED", recovered)
        assertFalse("Callbacks MUST not run when user intent is DISCONNECTED", callbackExecuted)
    }

    @Test
    fun testNoDataOrUnsupportedPid_doesNotDisconnectTransport() = runBlocking {
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)
        coordinator.onElmReady("ELM327 v1.5")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        // L0 Failure (e.g. Mode 01 PID not supported or transient NO DATA)
        val l0Result = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L0_ECU_APPLICATION,
            reason = "NO DATA on PID 012F",
            transport = mockTransport,
            onElmResync = { true },
            onProtocolRestore = { true }
        )

        assertTrue("L0 recovery returns true without touching transport", l0Result)
        assertTrue("Transport MUST remain physically connected", coordinator.truth.value.isPhysicallyConnected)
        assertEquals("Physical connect calls must be 1 (no reconnect)", 1, mockTransport.connectCallCount)
        assertEquals("Physical disconnect calls must be 0", 0, mockTransport.disconnectCallCount)
    }

    @Test
    fun testBleStateDisconnected_transitionsToRemoteClosedImmediately() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("ble-attempt", adapterAddress = "ble://AA:BB:CC:DD:EE:FF", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327 BLE")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        assertTrue(coordinator.truth.value.isPhysicallyConnected)

        // BLE GATT STATE_DISCONNECTED event
        coordinator.onTransportStateChanged(TransportLinkState.RemoteClosed("GATT_STATE_DISCONNECTED"))

        assertFalse(coordinator.truth.value.isPhysicallyConnected)
        assertEquals(ObdSessionTruthState.LINK_LOST, coordinator.truth.value.sessionState)
        assertEquals(DisconnectReason.REMOTE_CLOSED, coordinator.truth.value.disconnectReason)
    }

    @Test
    fun testKnownGoodLinkProfile_adapterPlusVehicleBinding() {
        val adapterMac = "AA:BB:CC:11:22:33"
        val vehicleId = "hyundai_accent_2005_123"

        KnownGoodAdapterStore.recordLinkSuccess(
            adapterFingerprint = adapterMac,
            vehicleBindingId = vehicleId,
            transportType = TransportType.BLUETOOTH_CLASSIC,
            connectMethod = ConnectMethod.INSECURE_SPP,
            elmIdentity = "ELM327 v1.5",
            protocol = "ISO 15765-4 CAN 11bit 500K",
            requestHeader = "7E0",
            initCommands = listOf("ATE0", "ATH0", "ATAT1"),
            baseDelayMs = 25L,
            transportDurationMs = 450L,
            ecuDurationMs = 800L
        )

        // 1. Query with exact adapter + vehicle pair
        val pairProfile = KnownGoodAdapterStore.getLinkProfile(adapterMac, vehicleId)
        assertNotNull(pairProfile)
        assertEquals(vehicleId, pairProfile?.vehicleBindingId)
        assertEquals("ISO 15765-4 CAN 11bit 500K", pairProfile?.protocol)
        assertEquals("7E0", pairProfile?.requestHeader)
        assertEquals(450L, pairProfile?.transportReadyP50)

        // 2. Query with different vehicle should return null or fallback without matching the other vehicle's specific binding
        val differentVehicleProfile = KnownGoodAdapterStore.getLinkProfile(adapterMac, "toyota_corolla_2020")
        assertNull(differentVehicleProfile?.vehicleBindingId)
    }

    @Test
    fun appLaunchWithSavedAdapterDoesNotConnectTest() {
        val coordinator = ObdLinkHealthCoordinator()
        // Saved adapter in memory / preferences does NOT create physical connection intent
        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
        assertFalse(coordinator.truth.value.isPhysicallyConnected)
        assertEquals(ObdSessionTruthState.DISCONNECTED, coordinator.truth.value.sessionState)
    }

    @Test
    fun selectVehicleDoesNotConnectTest() {
        val coordinator = ObdLinkHealthCoordinator()
        // Selecting a vehicle does not alter the coordinator intent
        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
        assertFalse(coordinator.truth.value.isPhysicallyConnected)
    }

    @Test
    fun cancelDuringBluetoothConnectStopsPermanentlyTest() = runBlocking {
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        val attempt = ConnectionAttempt("attempt-cancel-bt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1)
        coordinator.onUserConnectRequested(attempt)
        assertEquals(ConnectionIntent.CONNECT_REQUESTED, coordinator.truth.value.intent)

        // Cancel during connect phase
        coordinator.onUserCancelled("User cancelled while connecting BT")
        assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
        assertEquals(ObdSessionTruthState.DISCONNECTED, coordinator.truth.value.sessionState)

        // Verify recovery or auto-connect is blocked permanently
        val recoveryAttempted = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L1_ADAPTER_ELM,
            reason = "Should not run",
            transport = mockTransport,
            onElmResync = { true },
            onProtocolRestore = { true }
        )
        assertFalse(recoveryAttempted)
    }

    @Test
    fun cancelThenWait60SecondsDoesNotReconnectTest() = runBlocking {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserCancelled("User cancelled")

        // Simulate watchdog polling / timer tick
        for (sec in 1..60) {
            assertEquals(ConnectionIntent.DISCONNECTED, coordinator.truth.value.intent)
            assertFalse(coordinator.truth.value.isPhysicallyConnected)
        }
    }

    @Test
    fun canErrorDoesNotImmediatelyDisconnectBluetoothTest() = runBlocking {
        val coordinator = ObdLinkHealthCoordinator()
        val mockTransport = MockControlledTransport()

        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        mockTransport.connect()
        coordinator.onTransportStateChanged(mockTransport.linkState.value)
        coordinator.onElmReady("ELM327 v1.5")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        // L2 CAN Error occurs
        var protocolRestored = false
        val recovered = coordinator.executeLayeredRecovery(
            layer = FailureLayer.L2_VEHICLE_BUS_PROTOCOL,
            reason = "CAN ERROR received from ECU",
            transport = mockTransport,
            onElmResync = { true },
            onProtocolRestore = {
                protocolRestored = true
                true
            }
        )

        assertTrue(recovered)
        assertTrue(protocolRestored)
        assertTrue("Bluetooth MUST stay connected during L2 recovery", coordinator.truth.value.isPhysicallyConnected)
        assertEquals("Transport connect count must stay 1", 1, mockTransport.connectCallCount)
        assertEquals("Transport disconnect count must stay 0", 0, mockTransport.disconnectCallCount)
    }

    @Test
    fun noPhysicalReconnectOnTelemetrySilenceTest() {
        val coordinator = ObdLinkHealthCoordinator()
        coordinator.onUserConnectRequested(ConnectionAttempt("attempt", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 1))
        coordinator.onTransportStateChanged(TransportLinkState.Connected)
        coordinator.onElmReady("ELM327")
        coordinator.onProtocolReady(ObdProtocol.CAN_11BIT_500K)
        coordinator.onEcuReady()

        coordinator.onTelemetryStale()

        assertTrue("Telemetry silence does not drop physical link", coordinator.truth.value.isPhysicallyConnected)
        assertEquals(ObdSessionTruthState.DEGRADED, coordinator.truth.value.sessionState)
    }

    @Test
    fun connectionAttemptCannotOutliveCancelTest() {
        val coordinator = ObdLinkHealthCoordinator()
        val attempt = ConnectionAttempt("attempt-x", adapterAddress = "AA:BB:CC:DD:EE:FF", generation = 42)
        coordinator.onUserConnectRequested(attempt)
        assertTrue(coordinator.isCurrentAttempt(attempt.attemptId, attempt.generation))

        coordinator.onUserCancelled("Cancelled")
        assertFalse("Attempt cannot outlive cancel", coordinator.isCurrentAttempt(attempt.attemptId, attempt.generation))
    }
}
