package com.elysium.nexus.fabric.adapter.media

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
 * Media player adapter.
 *
 * Controls media playback on the Android device
 * or via cast protocols (Chromecast, DLNA/UPnP).
 * Translates media transport commands to
 * canonical capabilities.
 *
 * ## Why a stub
 *
 * Chromecast/DLNA discovery uses mDNS and
 * requires the Cast SDK or a UPnP library.
 * The stub defines the interface; the real
 * implementation ships when media control
 * is prioritized.
 */
class MediaAdapter : DeviceAdapter {

    override val protocol: Protocol = Protocol.Rtsp
    override val label: String = "Media Player"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.MediaTransport, Capability.PauseResume,
        Capability.Volume, Capability.Channel,
        Capability.InputSource
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    override suspend fun start(): AdapterResult { _state.value = AdapterState.Active; return AdapterResult.Ok }
    override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Error(ErrorCode.HardwareUnavailable, "Media scan requires Chromecast SDK or UPnP library.")
    override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.NotStarted, "Media adapter is a stub.")
    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Error(ErrorCode.NotStarted, "Media adapter is a stub.")
    override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Error(ErrorCode.NotStarted, "Media adapter is a stub.")
    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
    override suspend fun stop(): AdapterResult { _state.value = AdapterState.Released; return AdapterResult.Ok }
}
