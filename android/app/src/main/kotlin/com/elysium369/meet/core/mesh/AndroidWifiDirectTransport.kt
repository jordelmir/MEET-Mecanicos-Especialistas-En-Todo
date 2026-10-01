package com.elysium369.meet.core.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.*
import android.net.wifi.p2p.nsd.*
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native DNS-SD discovery and Wi-Fi Direct group setup, independent of Google Nearby. */
@SuppressLint("MissingPermission")
class AndroidWifiDirectTransport(context: Context, private val scope: CoroutineScope) : MeshDiscoveryTransport {
    private val manager = context.applicationContext.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var service: WifiP2pDnsSdServiceInfo? = null
    private var request: WifiP2pDnsSdServiceRequest? = null
    private val discovered = MutableSharedFlow<MeshPeerAdvertisement>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val discoveredPeers: Flow<MeshPeerAdvertisement> = discovered.asSharedFlow()
    private val ports = java.util.concurrent.ConcurrentHashMap<String, Int>()
    val lan = AndroidLanTransport(scope, MeshBearer.WIFI_DIRECT)
    private var port = 0
    private val appContext = context.applicationContext
    private fun channel(): WifiP2pManager.Channel = channel ?: manager?.initialize(appContext, Looper.getMainLooper()) { channel = null }?.also { channel = it } ?: error("WIFI_DIRECT_UNSUPPORTED")
    private suspend fun action(block: (WifiP2pManager.ActionListener) -> Unit) = withTimeout(15_000) {
        suspendCancellableCoroutine<Unit> { continuation ->
            block(object : WifiP2pManager.ActionListener {
                override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onFailure(reason: Int) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("WIFI_DIRECT_$reason")) }
            })
        }
    }
    override suspend fun startAdvertising(rotatingId: UUID) {
        val c = channel()
        if (port == 0) port = lan.listen()
        service?.let { old -> action { manager.removeLocalService(c, old, it) } }
        service = WifiP2pDnsSdServiceInfo.newInstance("ev-${rotatingId.toString().take(8)}", "_vanguardmesh._tcp", mapOf("v" to "1", "id" to rotatingId.toString(), "port" to port.toString()))
        action { manager.addLocalService(c, service, it) }
    }
    override suspend fun startScanning() {
        val c = channel()
        manager.setDnsSdResponseListeners(c, { _, _, _ -> }, { _, records, device ->
            if (records["v"] != "1") return@setDnsSdResponseListeners
            val id = records["id"]?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return@setDnsSdResponseListeners
            val remotePort = records["port"]?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: return@setDnsSdResponseListeners
            if (ports.size >= 64 && !ports.containsKey(device.deviceAddress)) return@setDnsSdResponseListeners
            ports[device.deviceAddress] = remotePort
            discovered.tryEmit(MeshPeerAdvertisement(id, MeshBearer.WIFI_DIRECT, device.deviceAddress, System.currentTimeMillis()))
        })
        if (request == null) {
            request = WifiP2pDnsSdServiceRequest.newInstance("_vanguardmesh._tcp")
            action { manager.addServiceRequest(c, request, it) }
        }
        action { manager.discoverServices(c, it) }
    }
    /** Local group owner waits for a client socket. Remote owner is connected at its advertised port. */
    suspend fun establish(peer: MeshPeerAdvertisement): MeshLink? {
        require(peer.bearer == MeshBearer.WIFI_DIRECT)
        val remotePort = ports[peer.address] ?: error("WIFI_DIRECT_PEER_STALE")
        val c = channel()
        action { manager.connect(c, WifiP2pConfig().apply { deviceAddress = peer.address; groupOwnerIntent = 0 }, it) }
        val info = withTimeout(30_000) {
            var connected: WifiP2pInfo? = null
            while (connected == null) {
                val current = suspendCancellableCoroutine<WifiP2pInfo> { cont -> manager.requestConnectionInfo(c) { if (cont.isActive) cont.resume(it) } }
                if (current.groupFormed) connected = current else delay(500)
            }
            connected
        }
        return if (info.isGroupOwner) null else lan.connect(info.groupOwnerAddress ?: error("WIFI_DIRECT_NO_ADDRESS"), remotePort)
    }
    override suspend fun stop() {
        lan.stop(); port = 0; ports.clear()
        val c = channel ?: return
        request?.let { r -> runCatching { action { manager.removeServiceRequest(c, r, it) } } }
        service?.let { s -> runCatching { action { manager.removeLocalService(c, s, it) } } }
        runCatching { action { manager.stopPeerDiscovery(c, it) } }
        runCatching { action { manager.cancelConnect(c, it) } }
        runCatching { action { manager.removeGroup(c, it) } }
        request = null; service = null; c.close(); channel = null
    }
}
