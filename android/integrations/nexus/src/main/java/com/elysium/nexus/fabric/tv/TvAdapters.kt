package com.elysium.nexus.fabric.tv

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * §9 Samsung Tizen TV Adapter.
 *
 * Protocol: WebSocket on port 8001 (art) or REST.
 * Discovery: SSDP `urn:samsung-com:device:ScreenCast:1`.
 * Pairing: PIN confirmation or key exchange.
 * State: WebSocket subscription.
 * Wake: Wake-on-LAN.
 *
 * ## Commands (JSON over WebSocket)
 *
 * ```json
 * {"method":"KEY_VOLUMEUP"}
 * {"method":"SWITCH_TO_SOURCE","params":"HDMI1"}
 * {"method":"RUN_APP","params":"111012001912"}
 * ```
 *
 * ## Maturity
 * CONCEPT — interface defined, implementation pending.
 */
class SamsungTizenTvAdapter : TvLanAdapter {

    override val brand: TvBrand = TvBrand.Samsung

    override val supportedProtocols: Set<Protocol> = setOf(
        Protocol.WiFi,
        Protocol.HdmiCec,
        Protocol.DirectIr
    )

    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource,
        Capability.MediaTransport
    )

    override suspend fun discover(timeoutMs: Long): List<TvDiscoveryRecord> = emptyList()
    override suspend fun identify(endpoint: String) = TvIdentityEvidence(
        brand = TvBrand.Samsung, model = null, modelName = null,
        serialNumber = null, macAddress = null, firmwareVersion = null,
        platform = "Tizen", protocols = supportedProtocols,
        capabilities = supportedCapabilities, confidence = 0.0
    )
    override suspend fun pair(request: PairingRequest) = PairingResult.Failed("Not implemented")
    override suspend fun queryCapabilities() = supportedCapabilities.map {
        // V0.7 Phase 25: readState() returns null — never advertise readable=true.
        TvCapability(it, readable = false, subscribable = false)
    }.toSet()
    override suspend fun execute(action: UniversalAction) = ActionExecutionResult.Unsupported(action::class.simpleName ?: "Unknown")
    override suspend fun readState(capability: com.elysium.nexus.fabric.canonical.Capability) = null
    override fun observeState(): Flow<DeviceStateChange> = emptyFlow()
    override suspend fun wake() = WakeResult.Unsupported
    override suspend fun disconnect() {}
}

/**
 * §9 Sony Bravia TV Adapter.
 *
 * Protocol: REST API (port 80) or WebSocket (port 80).
 * Discovery: mDNS `_sony-_audio._tcp` or SSDP.
 * Pairing: Authentication code / PIN.
 * State: REST polling or WebSocket subscription.
 * Wake: Wake-on-LAN or REST.
 *
 * ## Commands (REST JSON)
 *
 * ```json
 * POST /sony/system {"method":"setPowerStatus","params":[{"status":true}]}
 * POST /sony/avContent {"method":"setPlayContent","params":[{"uri":"extInput:hdmi?port=1"}]}
 * POST /sony/audio {"method":"setAudioVolume","params":[{"volume":20,"target":"speaker"}]}
 * ```
 *
 * ## Maturity
 * CONCEPT — interface defined, implementation pending.
 */
class SonyBraviaTvAdapter : TvLanAdapter {

    override val brand: TvBrand = TvBrand.Sony

    override val supportedProtocols: Set<Protocol> = setOf(
        Protocol.WiFi,
        Protocol.HdmiCec,
        Protocol.DirectIr
    )

    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource,
        Capability.MediaTransport
    )

    override suspend fun discover(timeoutMs: Long) = emptyList<TvDiscoveryRecord>()
    override suspend fun identify(endpoint: String) = TvIdentityEvidence(
        brand = TvBrand.Sony, model = null, modelName = null,
        serialNumber = null, macAddress = null, firmwareVersion = null,
        platform = "Android TV", protocols = supportedProtocols,
        capabilities = supportedCapabilities, confidence = 0.0
    )
    override suspend fun pair(request: PairingRequest) = PairingResult.Failed("Not implemented")
    override suspend fun queryCapabilities() = supportedCapabilities.map {
        // V0.7 Phase 25: readState() returns null — never advertise readable=true.
        TvCapability(it, readable = false, subscribable = false)
    }.toSet()
    override suspend fun execute(action: UniversalAction) = ActionExecutionResult.Unsupported(action::class.simpleName ?: "Unknown")
    override suspend fun readState(capability: com.elysium.nexus.fabric.canonical.Capability) = null
    override fun observeState(): Flow<DeviceStateChange> = emptyFlow()
    override suspend fun wake() = WakeResult.Unsupported
    override suspend fun disconnect() {}
}

/**
 * §9 Android / Google TV Adapter.
 *
 * Protocol: ADB (port 5555) or REST API.
 * Discovery: mDNS `_adb._tcp` or SSDP.
 * Pairing: ADB RSA key or companion app.
 * State: ADB dumpsys or REST polling.
 * Wake: Wake-on-LAN.
 *
 * ## Maturity
 * CONCEPT — interface defined, implementation pending.
 */
class AndroidGoogleTvAdapter : TvLanAdapter {

    override val brand: TvBrand = TvBrand.AndroidGoogle

    override val supportedProtocols: Set<Protocol> = setOf(
        Protocol.WiFi,
        Protocol.HdmiCec
    )

    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Volume,
        Capability.InputSource,
        Capability.MediaTransport
    )

    override suspend fun discover(timeoutMs: Long) = emptyList<TvDiscoveryRecord>()
    override suspend fun identify(endpoint: String) = TvIdentityEvidence(
        brand = TvBrand.AndroidGoogle, model = null, modelName = null,
        serialNumber = null, macAddress = null, firmwareVersion = null,
        platform = "Android TV", protocols = supportedProtocols,
        capabilities = supportedCapabilities, confidence = 0.0
    )
    override suspend fun pair(request: PairingRequest) = PairingResult.Failed("Not implemented")
    override suspend fun queryCapabilities() = supportedCapabilities.map {
        // V0.7 Phase 25: readState() returns null — never advertise readable=true.
        TvCapability(it, readable = false, subscribable = false)
    }.toSet()
    override suspend fun execute(action: UniversalAction) = ActionExecutionResult.Unsupported(action::class.simpleName ?: "Unknown")
    override suspend fun readState(capability: com.elysium.nexus.fabric.canonical.Capability) = null
    override fun observeState(): Flow<DeviceStateChange> = emptyFlow()
    override suspend fun wake() = WakeResult.Unsupported
    override suspend fun disconnect() {}
}
