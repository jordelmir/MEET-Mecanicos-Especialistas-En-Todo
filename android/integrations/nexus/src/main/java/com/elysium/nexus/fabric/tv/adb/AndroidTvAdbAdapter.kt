package com.elysium.nexus.fabric.tv.adb

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.discovery.MdnsDiscoveryProvider
import com.elysium.nexus.fabric.tv.ActionExecutionResult
import com.elysium.nexus.fabric.tv.DeviceStateChange
import com.elysium.nexus.fabric.tv.PairingRequest
import com.elysium.nexus.fabric.tv.PairingResult
import com.elysium.nexus.fabric.tv.TvBrand
import com.elysium.nexus.fabric.tv.TvCapability
import com.elysium.nexus.fabric.tv.TvDiscoveryRecord
import com.elysium.nexus.fabric.tv.TvIdentityEvidence
import com.elysium.nexus.fabric.tv.TvLanAdapter
import com.elysium.nexus.fabric.tv.WakeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * REAL ADB-based Android TV / Google TV / Fire TV
 * control over Wi-Fi (replaces the CONCEPT stub).
 *
 * - Discovery: mDNS `_adb._tcp` records (already
 *   implemented by [MdnsDiscoveryProvider]) + port
 *   5555 open check.
 * - Identify: device model via `shell:getprop
 *   ro.product.model`.
 * - Execute: `shell:input keyevent <KEYCODE>` — the
 *   same injection the TV's own remote uses.
 *
 * adbd authorization is handled by Android's standard
 * per-key "Allow USB debugging" dialog on the TV. After
 * that the TV remembers the RSA key.
 *
 * V0.7 Phase 25 — honesty contract:
 *  - This is a DEVELOPER_ONLY route (ADB is a debugging
 *    transport, never a consumer retail control path).
 *  - pair() is REAL: it only returns Success after an
 *    authorized connect + shell round-trip succeeds,
 *    proving the RSA key is accepted by the TV.
 *  - queryCapabilities() never advertises `readable`:
 *    readState() returns null (ADB has no state readback).
 *  - InputSource is NOT advertised: no keycode mapping
 *    exists for it.
 */
class AndroidTvAdbAdapter(
    private val ip: String,
    private val port: Int = ADB_PORT,
    private val authorization: AdbAuthorization = AdbAuthorization.generate(),
    private val context: android.content.Context? = null
) : TvLanAdapter {

    private val endpoint: String = "adb://${ip}:$port"

    override val brand: TvBrand = TvBrand.AndroidGoogle

    // V0.7 Phase 25: ADB Wi-Fi is strictly DEVELOPER_ONLY.
    // Protocol is WiFi (ADB over TCP). HdmiCec is removed until natively implemented.
    val isDeveloperOnlyRoute: Boolean = true

    override val supportedProtocols: Set<Protocol> = setOf(
        Protocol.WiFi
    )

    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Volume,
        Capability.MediaTransport
    )

    override suspend fun discover(timeoutMs: Long): List<TvDiscoveryRecord> {
        val records = context?.let { ctx ->
            runCatching {
                MdnsDiscoveryProvider(ctx).discover(timeoutMs * 2)
            }.getOrElse { emptyList() }
        } ?: emptyList()

        val adbHosts = records
            .filter { (it.rawProperties["serviceType"] ?: "").contains("_adb._tcp", ignoreCase = true) }
            .map { it.ipAddress to (it.port ?: ADB_PORT) }
            .mapNotNull { (ip, p) -> ip?.let { it to p } }
            .distinctBy { it.first }

        val open = filterAdbHosts(adbHosts.map { it.first })
        return open.map { ip ->
            TvDiscoveryRecord(
                brand = TvBrand.AndroidGoogle,
                ipAddress = ip,
                port = ADB_PORT,
                hostname = null,
                model = null,
                modelName = null,
                serialNumber = null,
                macAddress = null,
                firmwareVersion = null,
                protocol = Protocol.WiFi,
                friendlyName = "Android TV / Fire TV ($ip)",
                requiresPairing = true,
                wakeOnLanSupported = false
            )
        }
    }

    override suspend fun identify(endpoint: String): TvIdentityEvidence {
        val model = try {
            val client = AdbWirelessClient(ip, port)
            client.connect(authorization)
            val out = client.shell("getprop ro.product.model", authorization)
            client.disconnect()
            out.trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
        return TvIdentityEvidence(
            brand = brand,
            model = model,
            modelName = model,
            serialNumber = null,
            macAddress = null,
            firmwareVersion = null,
            platform = "Android TV / Google TV / Fire TV (ADB)",
            protocols = supportedProtocols,
            capabilities = supportedCapabilities,
            confidence = if (model != null) 1.0 else 0.5
        )
    }

    /**
     * REAL pairing: ADB pairing is the TV accepting this device's RSA key
     * (the standard "Allow USB debugging" dialog). Success is returned ONLY
     * after an authorized connect + shell round-trip proves the key is
     * accepted. No fake success without an authorized session.
     */
    override suspend fun pair(request: PairingRequest): PairingResult {
        return try {
            val client = AdbWirelessClient(ip, port)
            client.connect(authorization, authorizationTimeoutMs = request.timeoutMs.toInt())
            val out = client.shell("getprop ro.product.model", authorization)
            client.disconnect()
            if (out.isNullOrBlank()) {
                PairingResult.Failed("ADB authorized but getprop returned empty model")
            } else {
                PairingResult.Success("adb-key:${authorization.fingerprintSha256().take(12)}")
            }
        } catch (e: Exception) {
            PairingResult.Failed("ADB authorization not confirmed: ${e.message}")
        }
    }

    override suspend fun queryCapabilities() = supportedCapabilities.map {
        // V0.7 Phase 25: readState() always returns null for ADB —
        // `readable` must never be advertised as true.
        TvCapability(it, readable = false, subscribable = false)
    }.toSet()

    override suspend fun execute(action: UniversalAction): ActionExecutionResult {
        val keyCode = keyCodeFor(action)
            ?: return ActionExecutionResult.Unsupported(action::class.simpleName ?: "Action")

        return try {
            val startNs = System.nanoTime()
            withContext(Dispatchers.IO) {
                val client = AdbWirelessClient(ip, port)
                client.connect(authorization)
                val out = client.shell("input keyevent $keyCode", authorization)
                client.disconnect()
                ActionExecutionResult.Success(null, (System.nanoTime() - startNs) / 1_000_000)
            }
        } catch (e: Exception) {
            ActionExecutionResult.Failed("ADB ${e.message}", Protocol.WiFi)
        }
    }

    override suspend fun readState(capability: Capability) = null
    override fun observeState(): Flow<DeviceStateChange> = emptyFlow()
    override suspend fun wake() = WakeResult.Unsupported
    override suspend fun disconnect() {}

    private fun keyCodeFor(action: UniversalAction): Int? = when (action) {
        is UniversalAction.PowerOn,
        is UniversalAction.PowerOff,
        is UniversalAction.PowerToggle -> AndroidTvKeyCodes.POWER
        is UniversalAction.VolumeUp -> AndroidTvKeyCodes.VOLUME_UP
        is UniversalAction.VolumeDown -> AndroidTvKeyCodes.VOLUME_DOWN
        is UniversalAction.Mute -> AndroidTvKeyCodes.MUTE
        is UniversalAction.ChannelUp -> AndroidTvKeyCodes.CHANNEL_UP
        is UniversalAction.ChannelDown -> AndroidTvKeyCodes.CHANNEL_DOWN
        is UniversalAction.Back -> AndroidTvKeyCodes.BACK
        is UniversalAction.Home -> AndroidTvKeyCodes.HOME
        is UniversalAction.Menu -> AndroidTvKeyCodes.MENU
        is UniversalAction.Navigate -> when (action.direction) {
            com.elysium.nexus.fabric.canonical.Direction.Up -> AndroidTvKeyCodes.DPAD_UP
            com.elysium.nexus.fabric.canonical.Direction.Down -> AndroidTvKeyCodes.DPAD_DOWN
            com.elysium.nexus.fabric.canonical.Direction.Left -> AndroidTvKeyCodes.DPAD_LEFT
            com.elysium.nexus.fabric.canonical.Direction.Right -> AndroidTvKeyCodes.DPAD_RIGHT
        }
        is UniversalAction.Ok -> AndroidTvKeyCodes.DPAD_CENTER
        is UniversalAction.MediaPlay,
        is UniversalAction.MediaPause -> AndroidTvKeyCodes.MEDIA_PLAY_PAUSE
        is UniversalAction.MediaStop -> AndroidTvKeyCodes.MEDIA_STOP
        is UniversalAction.MediaNext -> AndroidTvKeyCodes.MEDIA_NEXT
        is UniversalAction.MediaPrevious -> AndroidTvKeyCodes.MEDIA_PREVIOUS
        else -> null
    }

    companion object {
        const val ADB_HOST = "127.0.0.1"
        const val ADB_PORT = 5555

        /** True when `host:port` answers (port 5555 open). */
        suspend fun isAdbEndpoint(host: String, port: Int = ADB_PORT, timeoutMs: Int = 700): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    socket.close()
                    true
                } catch (_: Exception) {
                    false
                }
            }

        /** Keep only the hosts with the ADB port open. */
        suspend fun filterAdbHosts(candidates: List<String>, port: Int = ADB_PORT, timeoutMs: Int = 700): List<String> =
            coroutineScope {
                candidates.map { ip ->
                    async(Dispatchers.IO) { ip to isAdbEndpoint(ip, port, timeoutMs) }
                }.awaitAll().filter { it.second }.map { it.first }
            }
    }

    private fun parseEndpoint(endpoint: String): Pair<String, Int> {
        val parts = endpoint.removePrefix("adb://").split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: ADB_PORT
        return host to port
    }
}