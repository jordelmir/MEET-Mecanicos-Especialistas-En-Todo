package com.elysium.nexus.fabric.adapter.applehome

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
 * Apple HomeKit adapter.
 *
 * Bridges HomeKit accessories into the canonical
 * model. HomeKit uses HAP (HomeKit Accessory
 * Protocol) over BLE or Wi-Fi. The adapter
 * discovers accessories via Bonjour/mDNS and
 * communicates via the HAP protocol.
 *
 * ## Why a stub
 *
 * HomeKit requires MFi certification or the
 * HomeKit Accessory Development Kit. The stub
 * defines the interface; the real implementation
 * ships when HomeKit integration is authorized.
 */
class AppleHomeAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.Ble
    override val label: String = "Apple HomeKit"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff, Capability.Level, Capability.Color,
        Capability.ColorTemperature, Capability.Temperature,
        Capability.TargetTemperature, Capability.FanSpeed,
        Capability.Position, Capability.OpenClose,
        Capability.LockUnlock, Capability.Scene,
        Capability.MotionDetection, Capability.ContactDetection
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.AuthFailed, "HomeKit requires MFi certification or HomeKit ADK.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.NotStarted, "HomeKit adapter is a stub.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "HomeKit adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.NotStarted, "HomeKit adapter is a stub.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }
}
