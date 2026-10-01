package com.elysium.nexus.fabric.adapter.zwave

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
 * Z-Wave adapter.
 *
 * Speaks Z-Wave (700/800 series) via the
 * Nexus Hub's Z-Wave controller. Translates
 * Z-Wave command classes to [Capability]s.
 *
 * ## Why a stub
 *
 * Z-Wave requires a hardware controller.
 * The stub defines the interface; the real
 * implementation ships with the Nexus Hub
 * firmware (Phase 4).
 */
class ZWaveAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.ZWave
    override val label: String = "Z-Wave"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Toggle,
        Capability.Level,
        Capability.Temperature,
        Capability.TargetTemperature,
        Capability.LockUnlock,
        Capability.Position,
        Capability.OpenClose,
        Capability.EnergyRead
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult {
        _state.value = AdapterState.Active
        return AdapterResult.Ok
    }

    override suspend fun scan(timeoutMs: Long): ScanResult {
        return ScanResult.Error(
            ErrorCode.HardwareUnavailable,
            "Z-Wave scan requires a 700/800 series controller via the Nexus Hub."
        )
    }

    override suspend fun read(deviceId: DeviceId): ReadResult {
        return ReadResult.Error(ErrorCode.NotStarted, "Z-Wave adapter is a stub.")
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
        return WriteResult.Error(ErrorCode.NotStarted, "Z-Wave adapter is a stub.")
    }

    override suspend fun subscribe(deviceId: DeviceId): AdapterResult {
        return AdapterResult.Error(ErrorCode.NotStarted, "Z-Wave adapter is a stub.")
    }

    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult {
        return AdapterResult.Ok
    }

    override suspend fun stop(): AdapterResult {
        _state.value = AdapterState.Released
        return AdapterResult.Ok
    }
}
