package com.elysium.nexus.fabric.adapter.infrared

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ConnectivityState
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.IrWaveform
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §10 Authoritative Infrared Device Adapter.
 *
 * Transmits physical IR signals resolved directly by exact [signalId] via [DeviceCommandResolver].
 * Hardcoded default NEC bytes (0x44, 0x45, 0x46) and fake "Online" states are strictly forbidden.
 */
class InfraredAdapter(
    private val context: Context? = null
) : DeviceAdapter {

    override val protocol: Protocol = Protocol.DirectIr
    override val label: String = "Infrared"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Level,
        Capability.MediaTransport,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    private var transmitter: AndroidIrTransmitter? = null

    companion object {
        private const val TAG = "InfraredAdapter"
    }

    override suspend fun start(): AdapterResult {
        _state.value = AdapterState.Starting
        transmitter = context?.let { AndroidIrTransmitter(it) }
        val hasEmitter = transmitter?.hasEmitter() == true
        Log.i(TAG, "IR emitter present: $hasEmitter")
        _state.value = AdapterState.Active
        return AdapterResult.Ok
    }

    override suspend fun scan(timeoutMs: Long): ScanResult {
        if (transmitter == null) {
            return ScanResult.Error(
                ErrorCode.HardwareUnavailable,
                "No IR emitter available."
            )
        }

        // P0-5: Discover installed IR profiles from Room, not from DeviceCatalog.
        // deviceId = "ir-${profile.id}" so DeviceCommandResolver can resolve by profile UUID.
        val profileRepo = context?.let { InstalledIrProfileRepository(it) }
        val installedProfiles = profileRepo?.getAllProfilesSuspend() ?: emptyList()

        val twins = installedProfiles.map { profile ->
            DeviceTwin(
                deviceId = DeviceId("ir-${profile.id}"),
                manufacturer = profile.brand,
                model = profile.model ?: profile.remoteModel ?: profile.displayName,
                deviceType = DeviceType.Television,
                capabilities = setOf(Capability.OnOff, Capability.Level, Capability.Volume),
                connectivity = ConnectivityState.Unknown,
                protocolBindings = setOf(
                    ProtocolBinding(
                        protocol = Protocol.DirectIr,
                        endpoint = "ir-${profile.id}",
                        capabilities = setOf(Capability.OnOff, Capability.Level, Capability.Volume)
                    )
                )
            )
        }

        // P0-10: Only Room installed profiles are DeviceTwins.
        // Templates are DiscoveryCandidates, not real devices.
        _devices.value = twins
        return ScanResult.Ok(deviceCount = twins.size)
    }

    override suspend fun read(deviceId: DeviceId): ReadResult {
        return ReadResult.Error(
            ErrorCode.UnsupportedOperation,
            "IR is transmit-only; no state to read."
        )
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
        val tx = transmitter
        if (tx == null || !tx.hasEmitter()) {
            return WriteResult.Error(
                ErrorCode.HardwareUnavailable,
                "No IR emitter available on this device."
            )
        }
        return when (state) {
            is DeviceState.IrCommand -> {
                // P0-6: Prefer full IrSignal for lossless transmission
                val waveform = if (state.irSignal != null) {
                    val encodeResult = com.elysium.nexus.fabric.infrared.IrProtocol.encode(state.irSignal)
                    when (encodeResult) {
                        is com.elysium.nexus.fabric.infrared.EncodeResult.Success -> encodeResult.waveform
                        else -> encodeIrCommand(state)
                    }
                } else {
                    encodeIrCommand(state)
                }
                if (waveform == null) {
                    return WriteResult.Error(
                        ErrorCode.UnsupportedOperation,
                        "Cannot encode IR command for protocol ${state.protocolName}."
                    )
                }
                val transmitResult = tx.transmit(waveform)
                if (transmitResult is IrTransmitResult.Success) {
                    Log.i(TAG, "IR transmitted: ${state.protocolName} addr=${state.address} cmd=${state.command}")
                    WriteResult.Ok(reportedState = state)
                } else {
                    WriteResult.Error(
                        ErrorCode.HardwareUnavailable,
                        "IR transmit failed ($transmitResult)."
                    )
                }
            }
            else -> WriteResult.Error(
                ErrorCode.UnsupportedOperation,
                "IR adapter requires DeviceState.IrCommand."
            )
        }
    }

    private fun encodeIrCommand(state: DeviceState.IrCommand): IrWaveform? {
        return when (state.protocolName.uppercase()) {
            "NEC" -> IrWaveform.encodeNec(state.address, state.command)
            "NECX", "NEC_EXTENDED" -> IrWaveform.encodeNecExtended(state.address, state.command)
            "RC5" -> IrWaveform.encodeRc5(state.address, state.command)
            "RC6" -> IrWaveform.encodeRc6(state.address, state.command)
            "SIRC", "SONY_SIRC" -> IrWaveform.encodeSonySirc(state.address, state.command)
            "SAMSUNG" -> IrWaveform.encodeSamsung(state.address, state.command)
            "KASEIKYO", "PANASONIC" -> IrWaveform.encodeKaseikyo(state.address, state.command)
            else -> null
        }
    }

    /**
     * Fix Section 10.1: Removed invented default NEC bytes (0x44, 0x45, 0x46).
     * Physical signals MUST be resolved via [DeviceCommandResolver] from exact profile bindings.
     */
    override fun translateAction(action: UniversalAction): DeviceState? {
        return null
    }

    override suspend fun subscribe(deviceId: DeviceId): AdapterResult {
        return AdapterResult.Error(
            ErrorCode.UnsupportedOperation,
            "IR is fire-and-forget; no state subscription."
        )
    }

    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok

    override suspend fun stop(): AdapterResult {
        transmitter = null
        _state.value = AdapterState.Released
        return AdapterResult.Ok
    }
}
