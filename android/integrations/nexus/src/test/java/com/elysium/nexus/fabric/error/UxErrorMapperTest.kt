package com.elysium.nexus.fabric.error

import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ConnectivityState
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.NexusError
import com.elysium.nexus.fabric.canonical.NexusErrorCode
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.dispatch.DispatchResult
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.routing.TransportRoute
import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.canonical.DeviceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P24: every terminal dispatch outcome is a typed, bilingual,
 * retryable-aware NexusError — Zero Failure Without Explanation.
 */
class UxErrorMapperTest {

    private val deviceId = DeviceId("tv-err-001")

    private fun adapter(protocol: Protocol): DeviceAdapter =
        object : DeviceAdapter {
            override val protocol: Protocol = protocol
            override val label: String = protocol.name
            override val supportedCapabilities: Set<Capability> = setOf(Capability.OnOff)
            private val _state = MutableStateFlow(AdapterState.Active)
            override val state: StateFlow<AdapterState> = _state
            private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
            override val devices: StateFlow<List<DeviceTwin>> = _devices
            override suspend fun start(): AdapterResult = AdapterResult.Ok
            override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Ok(0)
            override suspend fun read(deviceId: DeviceId): ReadResult =
                ReadResult.Error(com.elysium.nexus.fabric.adapter.ErrorCode.UnsupportedOperation, "fake")
            override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult =
                WriteResult.Ok(reportedState = state)
            override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
            override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
            override suspend fun stop(): AdapterResult = AdapterResult.Ok
        }

    private val twin = DeviceTwin(
        deviceId = deviceId,
        deviceType = DeviceType.Television,
        capabilities = setOf(Capability.OnOff),
        connectivity = ConnectivityState.Online,
        label = "Err TV",
        protocolBindings = setOf(
            ProtocolBinding(Protocol.DirectIr, "ir-1", setOf(Capability.OnOff))
        )
    )

    private val route: TransportRoute by lazy {
        RouteNegotiator(listOf(adapter(Protocol.DirectIr)))
            .negotiate(UniversalAction.PowerOn(deviceId), twin)
            .first()
    }

    @Test
    fun `success maps to no error`() {
        val result = DispatchResult.Success(
            action = UniversalAction.PowerOn(deviceId),
            route = route,
            latencyMs = 5,
            reportedState = null
        )
        assertNull(UxErrorMapper.fromDispatchResult(result))
        assertNull(UxErrorMapper.codeFor(result))
    }

    @Test
    fun `no target maps to IdentityNotFound with device context`() {
        val error = UxErrorMapper.fromDispatchResult(DispatchResult.NoTarget(deviceId))!!
        assertEquals(NexusErrorCode.IdentityNotFound, error.code)
        assertEquals(deviceId, error.deviceId)
        assertEquals(false, error.isRetryable)
        assertEquals(com.elysium.nexus.fabric.canonical.ErrorSeverity.Error, error.severity)
        assertTrue(error is NexusError.IdentityNotFound)
    }

    @Test
    fun `no route maps to DeviceUnreachable`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.NoRoute(UniversalAction.PowerOn(deviceId), twin)
        )!!
        assertEquals(NexusErrorCode.DeviceUnreachable, error.code)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `permission denied maps to PermissionDenied carrying the missing list`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.PermissionDenied(
                UniversalAction.PowerOn(deviceId),
                listOf("android.permission.TRANSMIT_IR")
            )
        )!!
        assertEquals(NexusErrorCode.PermissionDenied, error.code)
        assertEquals(listOf("android.permission.TRANSMIT_IR"), (error as NexusError.PermissionDenied).missingPermissions)
        assertEquals(false, error.isRetryable)
    }

    @Test
    fun `translation failed maps to ProtocolError with protocol`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.TranslationFailed(UniversalAction.PowerOn(deviceId), route)
        )!!
        assertEquals(NexusErrorCode.ProtocolError, error.code)
        assertEquals(Protocol.DirectIr, error.protocol)
    }

    @Test
    fun `adapter timeout maps to Timeout`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.AdapterFailed(
                UniversalAction.PowerOn(deviceId),
                route,
                WriteResult.Error(ErrorCode.Timeout, "no ack in 3s")
            )
        )!!
        assertEquals(NexusErrorCode.Timeout, error.code)
        assertTrue(error.isRetryable)
        assertEquals(Protocol.DirectIr, error.protocol)
    }

    @Test
    fun `adapter auth maps to AuthFailed, non retryable`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.AdapterFailed(
                UniversalAction.PowerOn(deviceId),
                route,
                WriteResult.Error(ErrorCode.AuthFailed, "pairing key stale")
            )
        )!!
        assertEquals(NexusErrorCode.AuthFailed, error.code)
        assertEquals(false, error.isRetryable)
    }

    @Test
    fun `every adapter ErrorCode maps without throwing`() {
        for (code in ErrorCode.entries) {
            val error = UxErrorMapper.fromAdapterError(
                WriteResult.Error(code, "e"),
                protocol = Protocol.DirectIr,
                deviceId = deviceId
            )
            assertNotNull("ErrorCode $code must map", error)
        }
    }

    @Test
    fun `all-routes-failed on open circuit maps to ResourceExhausted`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.AllRoutesFailed(
                UniversalAction.PowerOn(deviceId),
                "Circuit open for DirectIr"
            )
        )!!
        assertEquals(NexusErrorCode.ResourceExhausted, error.code)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `all-routes-failed exhaustion maps to NetworkError`() {
        val error = UxErrorMapper.fromDispatchResult(
            DispatchResult.AllRoutesFailed(UniversalAction.PowerOn(deviceId), "All routes exhausted")
        )!!
        assertEquals(NexusErrorCode.NetworkError, error.code)
    }

    @Test
    fun `every taxonomy code has a bilingual explanation with cause and action`() {
        for (code in NexusErrorCode.entries) {
            val explanation = UxErrorMapper.explanation(code)
            assertNotNull("missing explanation for $code", explanation)
            val en = explanation!!.en
            val es = explanation.es
            assertTrue("en title for $code", en.title.isNotBlank())
            assertTrue("en cause for $code", en.cause.isNotBlank())
            assertTrue("en action for $code", en.action.isNotBlank())
            assertTrue("es title for $code", es.title.isNotBlank())
            assertTrue("es cause for $code", es.cause.isNotBlank())
            assertTrue("es action for $code", es.action.isNotBlank())
        }
        assertEquals(NexusErrorCode.entries.size, UxErrorMapper.all().size)
    }

    @Test
    fun `explanations distinguish cause from action`() {
        val p = UxErrorMapper.explanation(NexusErrorCode.PermissionDenied)!!
        val en = p.en
        assertNotEquals(en.cause, en.action)
        val es = p.forLanguage(spanish = true)
        assertTrue(es.title != p.en.title)
        assertTrue(es.cause.isNotBlank())
    }

    private fun assertNotEquals(a: String, b: String) {
        assertTrue("expected '$a' != '$b'", a != b)
    }
}