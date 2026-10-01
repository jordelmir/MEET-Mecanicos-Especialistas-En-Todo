package com.elysium.nexus.fabric.adapter.vendor

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
 * Vendor REST / WebSocket adapter.
 *
 * Generic adapter for devices that expose a
 * proprietary REST or WebSocket API. The adapter
 * is configured with endpoint mappings that
 * translate canonical capabilities to vendor-
 * specific HTTP calls.
 *
 * ## Why a stub
 *
 * Each vendor's API is unique; the stub
 * defines the interface. The real
 * implementation accepts a configuration
 * object that maps capabilities to endpoints.
 */
class VendorAdapter(
    private val vendorName: String,
    private val baseUrl: String,
    private val authHeader: String? = null
) : DeviceAdapter {

    override val protocol: Protocol = Protocol.VendorRest
    override val label: String = vendorName
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff, Capability.Level, Capability.Color,
        Capability.Temperature, Capability.TargetTemperature,
        Capability.Position, Capability.OpenClose,
        Capability.LockUnlock, Capability.Scene,
        Capability.MediaTransport, Capability.Volume
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.UnsupportedOperation, "Vendor adapter requires manual device configuration.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.NotStarted, "Vendor adapter is a stub.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "Vendor adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.NotStarted, "Vendor adapter is a stub.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }
}
