package com.elysium.nexus.fabric.adapter.alexa

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Alexa adapter.
 *
 * Bridges Alexa Smart Home API devices into the
 * canonical model. Alexa exposes devices via the
 * Alexa Events API (proactive events) and
 * Directive/Report interface.
 *
 * ## Why a stub
 *
 * Alexa Smart Home requires an Alexa Skills Kit
 * lambda and OAuth2. The stub defines the
 * interface; the real implementation ships
 * when voice integration is prioritized.
 */
class AlexaAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.VendorRest
    override val label: String = "Amazon Alexa"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff, Capability.Level, Capability.Color,
        Capability.Temperature, Capability.TargetTemperature,
        Capability.Scene, Capability.LockUnlock,
        Capability.MediaTransport, Capability.Volume
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.AuthFailed, "Alexa requires OAuth2 + Alexa Skills Kit lambda.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.NotStarted, "Alexa adapter is a stub.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "Alexa adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.NotStarted, "Alexa adapter is a stub.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }
}
