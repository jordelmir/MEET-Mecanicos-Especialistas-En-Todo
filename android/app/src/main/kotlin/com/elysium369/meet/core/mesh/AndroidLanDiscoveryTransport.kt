package com.elysium369.meet.core.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native local mDNS. Names are rotating random tokens; resolves at most 64 transient local endpoints. */
class AndroidLanDiscoveryTransport(context: Context, private val scope: CoroutineScope) : MeshDiscoveryTransport, MeshLinkFactory {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    val lan = AndroidLanTransport(scope)
    private val peers = Channel<MeshPeerAdvertisement>(64)
    override val discoveredPeers: Flow<MeshPeerAdvertisement> = peers.receiveAsFlow()
    private val endpoints = ConcurrentHashMap<String, InetSocketAddress>()
    private val resolveQueue = Channel<NsdServiceInfo>(64)
    private var resolveJob: Job? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var port = 0
    override suspend fun startAdvertising(rotatingId: UUID) {
        registration?.let { nsd.unregisterService(it) }; registration = null
        if (port == 0) port = lan.listen()
        val completion = CompletableDeferred<Unit>()
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { completion.complete(Unit) }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { completion.completeExceptionally(IllegalStateException("LAN_REGISTER_$code")) }
            override fun onServiceUnregistered(info: NsdServiceInfo) { }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) { }
        }
        registration = callback
        nsd.registerService(NsdServiceInfo().apply { serviceName = "ev-${rotatingId}"; serviceType = TYPE; port = this@AndroidLanDiscoveryTransport.port }, NsdManager.PROTOCOL_DNS_SD, callback)
        withTimeout(15_000) { completion.await() }
    }
    override suspend fun startScanning() {
        if (discovery != null) return
        resolveJob = scope.launch {
            for (service in resolveQueue) {
                try {
                    val resolved = withTimeout(10_000) {
                        suspendCancellableCoroutine<NsdServiceInfo> { cont -> nsd.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { if (cont.isActive) cont.resumeWithException(IllegalStateException("LAN_RESOLVE_$code")) }
                            override fun onServiceResolved(info: NsdServiceInfo) { if (cont.isActive) cont.resume(info) }
                        }) }
                    }
                    if (endpoints.size >= 64 && !endpoints.containsKey(resolved.serviceName)) continue
                    val host = resolved.host ?: continue
                    if (!(host.isSiteLocalAddress || host.isLinkLocalAddress || host.isLoopbackAddress) || resolved.port !in 1024..65535) continue
                    endpoints[resolved.serviceName] = InetSocketAddress(host, resolved.port)
                    peers.trySend(MeshPeerAdvertisement(resolved.serviceName.removePrefix("ev-"), MeshBearer.LAN, resolved.serviceName, System.currentTimeMillis()))
                } catch (e: Exception) { if (e is CancellationException) throw e }
            }
        }
        val completion = CompletableDeferred<Unit>()
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { completion.complete(Unit) }
            override fun onDiscoveryStopped(type: String) { }
            override fun onStartDiscoveryFailed(type: String, code: Int) { completion.completeExceptionally(IllegalStateException("LAN_DISCOVERY_$code")) }
            override fun onStopDiscoveryFailed(type: String, code: Int) { }
            override fun onServiceFound(info: NsdServiceInfo) { if (info.serviceName.startsWith("ev-") && info.serviceName.length <= 48) resolveQueue.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) { endpoints.remove(info.serviceName) }
        }
        discovery = callback
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, callback)
        withTimeout(15_000) { completion.await() }
    }
    override suspend fun connect(peer: MeshPeerAdvertisement): MeshLink {
        require(peer.bearer == MeshBearer.LAN)
        val address = endpoints[peer.address] ?: error("LAN_PEER_STALE")
        return lan.connect(address.address, address.port)
    }
    override suspend fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }; discovery = null
        registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null
        resolveJob?.cancelAndJoin(); resolveJob = null
        endpoints.clear(); while (resolveQueue.tryReceive().isSuccess) { }; while (peers.tryReceive().isSuccess) { }
        lan.stop(); port = 0
    }
    companion object { private const val TYPE = "_vanguardmesh._tcp." }
}
