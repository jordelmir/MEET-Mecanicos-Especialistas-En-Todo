package com.elysium369.meet.core.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.aware.*
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native Aware discovery and WPA-protected data path. PSK comparison is explicit on both phones.
 * Aware WPA protects the link; only a reviewed MeshCryptoSuite can establish content E2EE.
 */
@SuppressLint("MissingPermission")
class AndroidWifiAwareTransport(context: Context, private val scope: CoroutineScope) : MeshDiscoveryTransport {
    private val context = context.applicationContext
    private val manager = context.getSystemService(WifiAwareManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var aware: WifiAwareSession? = null
    private var publisher: PublishDiscoverySession? = null
    private var subscriber: SubscribeDiscoverySession? = null
    private var receiverRegistered = false
    private var localInfo = ByteArray(0)
    private var listeningPort = 0
    private var passphrase: String? = null
    private data class Peer(val handle: PeerHandle, val port: Int)
    private val handles = ConcurrentHashMap<String, Peer>()
    private val requests = ConcurrentHashMap<ConnectivityManager.NetworkCallback, MeshLinkBudget.Lease>()
    private val pathBudget = MeshLinkBudget()
    private val discovered = MutableSharedFlow<MeshPeerAdvertisement>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val discoveredPeers: Flow<MeshPeerAdvertisement> = discovered.asSharedFlow()
    val status = MutableStateFlow("Wi-Fi Aware apagado")
    val lan = AndroidLanTransport(scope, MeshBearer.WIFI_AWARE)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) {
                status.value = "Wi-Fi Aware cambió; vuelve a activar para descubrir"
                scope.launch { stop() }
            }
        }
    }
    /** Must be entered/compared in person; never derived from a principal ID or public beacon. */
    fun setLinkPassphrase(value: String) { require(value.length in 8..63 && value.all { it.code in 32..126 }); passphrase = value }
    private suspend fun attach(): WifiAwareSession {
        aware?.let { return it }
        if (!receiverRegistered) { ContextCompat.registerReceiver(context, receiver, IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED); receiverRegistered = true }
        check(manager?.isAvailable == true) { "WIFI_AWARE_UNAVAILABLE" }
        return withTimeout(15_000) {
            suspendCancellableCoroutine { continuation ->
                manager.attach(object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        if (continuation.isActive) { aware = session; continuation.resume(session) } else session.close()
                    }
                    override fun onAttachFailed() { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("WIFI_AWARE_ATTACH_FAILED")) }
                }, null)
            }
        }
    }
    override suspend fun startAdvertising(rotatingId: UUID) {
        val session = attach()
        val port = if (listeningPort == 0) lan.listen().also { listeningPort = it } else listeningPort
        localInfo = "${rotatingId}|$port".toByteArray(Charsets.US_ASCII)
        publisher?.close()
        val completion = CompletableDeferred<Unit>()
        session.publish(PublishConfig.Builder().setServiceName(SERVICE).setServiceSpecificInfo(localInfo).build(), object : DiscoverySessionCallback() {
            override fun onPublishStarted(s: PublishDiscoverySession) { publisher = s; completion.complete(Unit) }
            override fun onSessionConfigFailed() { completion.completeExceptionally(IllegalStateException("WIFI_AWARE_PUBLISH_FAILED")) }
            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                if (message.size !in 1..64 || message.toString(Charsets.US_ASCII) != "PATH1") return
                val key = passphrase ?: return
                if (requests.size >= 4) return
                val pub = publisher ?: return
                scope.launch { runCatching { requestPath(pub, peerHandle, key, port) }; status.value = "Wi-Fi Aware: esperando enlace autenticado" }
            }
        }, null)
        withTimeout(15_000) { completion.await() }
        status.value = "Wi-Fi Aware: anuncio activo"
    }
    override suspend fun startScanning() {
        val session = attach()
        if (subscriber != null) return
        val completion = CompletableDeferred<Unit>()
        session.subscribe(SubscribeConfig.Builder().setServiceName(SERVICE).build(), object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(s: SubscribeDiscoverySession) { subscriber = s; completion.complete(Unit) }
            override fun onSessionConfigFailed() { completion.completeExceptionally(IllegalStateException("WIFI_AWARE_SUBSCRIBE_FAILED")) }
            override fun onServiceDiscovered(handle: PeerHandle, serviceSpecificInfo: ByteArray, matchFilter: MutableList<ByteArray>) {
                if (serviceSpecificInfo.size !in 1..64) return
                val parts = serviceSpecificInfo.toString(Charsets.US_ASCII).split('|')
                if (parts.size != 2 || runCatching { UUID.fromString(parts[0]) }.isFailure) return
                val port = parts[1].toIntOrNull()?.takeIf { it in 1024..65535 } ?: return
                if (handles.size >= 64 && !handles.containsKey(parts[0])) return
                handles[parts[0]] = Peer(handle, port)
                discovered.tryEmit(MeshPeerAdvertisement(parts[0], MeshBearer.WIFI_AWARE, parts[0], System.currentTimeMillis()))
            }
            override fun onSessionTerminated() { subscriber = null; handles.clear(); status.value = "Wi-Fi Aware: sesión terminada" }
        }, null)
        withTimeout(15_000) { completion.await() }
    }
    suspend fun establish(peer: MeshPeerAdvertisement): MeshLink {
        require(peer.bearer == MeshBearer.WIFI_AWARE)
        val key = passphrase ?: error("WIFI_AWARE_COMPARE_PASSPHRASE_FIRST")
        val remote = handles[peer.rotatingId] ?: error("WIFI_AWARE_PEER_STALE")
        val sub = subscriber ?: error("WIFI_AWARE_NOT_SCANNING")
        sub.sendMessage(remote.handle, 1, "PATH1".toByteArray(Charsets.US_ASCII))
        val path = requestPath(sub, remote.handle, key, null)
        val address = path.second ?: error("WIFI_AWARE_ADDRESS_REQUIRES_API29")
        return lan.connect(address, remote.port, path.first)
    }
    private suspend fun requestPath(session: DiscoverySession, peer: PeerHandle, key: String, port: Int?): Pair<Network, InetAddress?> {
        val reservation = pathBudget.reserve() ?: error("WIFI_AWARE_PATH_CAPACITY")
        val specifier = try { if (Build.VERSION.SDK_INT >= 29) WifiAwareNetworkSpecifier.Builder(session, peer).setPskPassphrase(key).apply { if (port != null) setPort(port) }.build() else session.createNetworkSpecifierPassphrase(peer, key) } catch (e: Exception) { reservation.close(); throw e }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(specifier).build()
        val ready = CompletableDeferred<Pair<Network, InetAddress?>>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (Build.VERSION.SDK_INT >= 29) {
                    val info = capabilities.transportInfo as? WifiAwareNetworkInfo
                    if (info != null) ready.complete(network to info.peerIpv6Addr)
                } else ready.complete(network to null)
            }
            override fun onUnavailable() { ready.completeExceptionally(IllegalStateException("WIFI_AWARE_PATH_UNAVAILABLE")) }
            override fun onLost(network: Network) { requests.remove(this)?.close(); runCatching { connectivity.unregisterNetworkCallback(this) }; status.value = "Wi-Fi Aware: enlace perdido" }
        }
        requests[callback] = reservation
        try { connectivity.requestNetwork(request, callback, 20_000); return withTimeout(25_000) { ready.await() } }
        catch (e: Exception) { requests.remove(callback)?.close(); runCatching { connectivity.unregisterNetworkCallback(callback) }; throw e }
    }
    override suspend fun stop() {
        requests.keys.toList().forEach { runCatching { connectivity.unregisterNetworkCallback(it) }; requests.remove(it)?.close() }
        subscriber?.close(); publisher?.close(); aware?.close(); subscriber = null; publisher = null; aware = null
        handles.clear(); passphrase = null; lan.stop(); listeningPort = 0
        if (receiverRegistered) { context.unregisterReceiver(receiver); receiverRegistered = false }
        status.value = "Wi-Fi Aware apagado"
    }
    companion object { private const val SERVICE = "elysium-vanguard-mesh-v1" }
}
