package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.LockSource
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [AdapterActionDispatcher].
 * Uses a fake adapter to verify command
 * routing, error mapping, and unsupported
 * command handling.
 */
class AdapterActionDispatcherTest {

    @Test
    fun dispatch_returnsAccepted_whenAdapterWriteSucceeds() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
    }

    @Test
    fun dispatch_returnsDeviceOffline_whenAdapterIsOffline() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Error(ErrorCode.DeviceOffline, "device offline")
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.DeviceOffline, status)
    }

    @Test
    fun dispatch_returnsTimedOut_whenAdapterTimesOut() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Error(ErrorCode.Timeout, "timeout")
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.TimedOut, status)
    }

    @Test
    fun dispatch_returnsUnsupported_forNoopCommand() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.Noop
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Unsupported, status)
    }

    @Test
    fun dispatch_returnsUnsupported_whenNoAdapterSupportsCapability() {
        val dispatcher = AdapterActionDispatcher(
            mapOf(
                Protocol.DirectIr to FakeAdapter(
                    supportedCapabilities = setOf(Capability.Level),
                    writeResult = WriteResult.Ok(null)
                )
            )
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Unsupported, status)
    }

    @Test
    fun dispatch_routesLevelCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.Level),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("light-1"),
            capability = Capability.Level,
            command = CommandValue.Level(0.75f)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
        assertEquals(DeviceState.Level(0.75f), adapter.lastWrittenState)
    }

    @Test
    fun dispatch_routesColorCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.Color),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("light-1"),
            capability = Capability.Color,
            command = CommandValue.Color(hueDegrees = 180f, saturation = 0.9f)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
        assertEquals(
            DeviceState.Color(hueDegrees = 180f, saturation = 0.9f),
            adapter.lastWrittenState
        )
    }

    @Test
    fun dispatch_routesClimateCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.Temperature),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("ac-1"),
            capability = Capability.Temperature,
            command = CommandValue.Climate(targetCelsius = 24f)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
    }

    @Test
    fun dispatch_routesLockCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.LockUnlock),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("lock-1"),
            capability = Capability.LockUnlock,
            command = CommandValue.Lock(locked = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
        assertEquals(DeviceState.Lock(locked = true, source = LockSource.App), adapter.lastWrittenState)
    }

    @Test
    fun dispatch_routesPositionCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.Position),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("blind-1"),
            capability = Capability.Position,
            command = CommandValue.Position(percentOpen = 0.5f)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
        assertEquals(
            DeviceState.Position(percentOpen = 0.5f),
            adapter.lastWrittenState
        )
    }

    @Test
    fun dispatch_routesMediaCommandToAdapter() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.MediaTransport),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.MediaTransport,
            command = CommandValue.Media(play = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
        assertEquals(DeviceState.Media(playing = true), adapter.lastWrittenState)
    }

    @Test
    fun dispatch_returnsUnsupported_forUnsupportedOperation() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Error(ErrorCode.UnsupportedOperation, "not supported")
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Unsupported, status)
    }

    @Test
    fun dispatch_returnsRejected_forGenericError() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.OnOff),
            writeResult = WriteResult.Error(ErrorCode.Unknown, "something broke")
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("tv-1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Rejected, status)
    }

    @Test
    fun dispatch_returnsNull_forColorTemperatureCommand() {
        val adapter = FakeAdapter(
            supportedCapabilities = setOf(Capability.ColorTemperature),
            writeResult = WriteResult.Ok(null)
        )
        val dispatcher = AdapterActionDispatcher(
            mapOf(Protocol.DirectIr to adapter)
        )
        val action = Action(
            deviceId = DeviceId("light-1"),
            capability = Capability.ColorTemperature,
            command = CommandValue.ColorTemperature(kelvin = 4000)
        )
        val status = dispatcher.dispatch(action, defaultPolicy())
        assertEquals(CommandStatus.Accepted, status)
    }

    @Test
    fun defaultPolicy_hasExpectedValues() {
        val policy = defaultPolicy()
        assertEquals(5_000L, policy.timeoutMs)
        assertEquals(false, policy.requireStateConfirmation)
    }

    private fun defaultPolicy() = VerificationPolicy(
        timeoutMs = 5_000L,
        requireStateConfirmation = false
    )

    /**
     * A fake adapter for unit tests.
     */
    private class FakeAdapter(
        override val supportedCapabilities: Set<Capability>,
        private val writeResult: WriteResult
    ) : DeviceAdapter {
        var lastWrittenState: DeviceState? = null
            private set

        override val protocol: Protocol = Protocol.DirectIr
        override val label: String = "FakeAdapter"
        override val state = MutableStateFlow(AdapterState.Idle)
        override val devices = MutableStateFlow(emptyList<DeviceTwin>())

        override suspend fun start() = AdapterResult.Ok
        override suspend fun scan(timeoutMs: Long) = ScanResult.Ok(0)
        override suspend fun read(deviceId: DeviceId) =
            com.elysium.nexus.fabric.adapter.ReadResult.Error(
                ErrorCode.UnsupportedOperation, "fake"
            )
        override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
            lastWrittenState = state
            return writeResult
        }
        override suspend fun subscribe(deviceId: DeviceId) = AdapterResult.Ok
        override suspend fun unsubscribe(deviceId: DeviceId) = AdapterResult.Ok
        override suspend fun stop() = AdapterResult.Ok
    }
}
