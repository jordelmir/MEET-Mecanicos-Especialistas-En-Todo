package com.elysium.nexus.fabric.adapter.ble

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
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE (Bluetooth Low Energy) adapter.
 *
 * Discovers and controls BLE GATT devices.
 * Translates GATT services/characteristics
 * to canonical capabilities. Covers BLE
 * sensors, bulbs, locks, and fitness devices.
 *
 * ## Why a stub
 *
 * BLE GATT discovery and characteristic
 * mapping requires per-device service UUIDs.
 * The stub defines the interface; the real
 * implementation ships when the BLE device
 * database is populated.
 */
class BleAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.Ble
    override val label: String = "Bluetooth Low Energy"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff, Capability.Toggle, Capability.Level,
        Capability.Temperature, Capability.AirQuality,
        Capability.ContactDetection, Capability.MotionDetection,
        Capability.EnergyRead
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.HardwareUnavailable, "BLE scan requires a GATT database mapping service UUIDs to capabilities.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.NotStarted, "BLE adapter is a stub.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "BLE adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.NotStarted, "BLE adapter is a stub.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }

    /**
     * Translate a [UniversalAction] into BLE-specific
     * [DeviceState]. Maps power/volume/media to on/off
     * and level states for BLE GATT devices.
     */
    override fun translateAction(action: UniversalAction): DeviceState? {
        return when (action) {
            is UniversalAction.PowerOn -> DeviceState.OnOff(isOn = true)
            is UniversalAction.PowerOff -> DeviceState.OnOff(isOn = false)
            is UniversalAction.PowerToggle -> DeviceState.OnOff(isOn = true)
            is UniversalAction.SetVolume -> DeviceState.Level(value = action.level)
            is UniversalAction.SetFanSpeed -> DeviceState.Level(value = action.level)
            else -> null // BLE adapter stub: most actions pending GATT database
        }
    }
}
