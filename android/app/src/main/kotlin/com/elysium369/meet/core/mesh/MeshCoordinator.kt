package com.elysium369.meet.core.mesh

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Local radio custody and plaintext receipt are not business approval, report validation or payment. */
data class MeshRuntimeState(val enabled: Boolean = false, val peers: List<MeshPeerAdvertisement> = emptyList(), val links: Int = 0, val detail: String = "Mesh nativo apagado", val cryptoVerified: Boolean = false)
data class MeshRelayPolicy(val optedIn: Boolean = false, val chargingOnly: Boolean = true, val batteryThreshold: Int = 20) {
    fun allowed(batteryPercent: Int, charging: Boolean) = optedIn && batteryPercent in batteryThreshold..100 && (!chargingOnly || charging)
}

/** Reviewed provider is a required capability. Transport-only discovery remains available when absent. */
class MeshRouter(private val repository: MeshRepository, private val crypto: MeshCryptoSuite, private val limits: MeshLimits = MeshLimits()) {
    suspend fun receive(frame: ByteArray, peerKeyId: String): MeshIntake {
        if (!crypto.independentlyReviewed) return MeshIntake.CRYPTO_UNAVAILABLE
        val packet = try { MeshPacketWire.decode(frame) } catch (_: Exception) { return MeshIntake.MALFORMED }
        when (packet) {
            is MeshPacket.Chunk -> return if (repository.storeChunk(packet.messageId, packet.index, packet.bytes)) MeshIntake.STORED else MeshIntake.MALFORMED
            is MeshPacket.Receipt -> return if (receipt(packet.receipt, peerKeyId)) MeshIntake.STORED else MeshIntake.UNAUTHENTICATED
            is MeshPacket.Envelope -> Unit
        }
        val envelope = (packet as MeshPacket.Envelope).envelope
        limits.rejection(envelope, System.currentTimeMillis())?.let { return it }
        if (!crypto.authenticate(envelope)) return MeshIntake.UNAUTHENTICATED
        return repository.storeAuthenticated(envelope)
    }
    suspend fun receipt(receipt: MeshCustodyReceipt, peerKeyId: String): Boolean {
        if (!crypto.independentlyReviewed || receipt.peerKeyId != peerKeyId || !crypto.verifyReceipt(receipt)) return false
        return repository.recordVerifiedCustody(receipt)
    }
    /** Socket send success proves neither recipient receipt nor delivery; durable custody is retained. */
    suspend fun forward(link: MeshLink, relayPolicy: MeshRelayPolicy, batteryPercent: Int, charging: Boolean) {
        check(relayPolicy.allowed(batteryPercent, charging)) { "MESH_RELAY_CONSENT_OR_BATTERY" }
        check(crypto.independentlyReviewed) { "MESH_CRYPTO_PROVIDER_NOT_VERIFIED" }
        for (envelope in repository.pending()) {
            val forwarded = MeshPacketWire.encode(MeshPacket.Envelope(envelope.copy(hops = envelope.hops + 1)))
            if (forwarded.size <= link.bearer.maxFrameBytes) link.send(forwarded)
            // Large attachments remain in durable custody and use explicit bounded chunk transfer.
        }
    }
    /** Transfers encrypted attachment chunks; receiver authenticates their signed manifest digest. */
    suspend fun transferAttachment(id: String, link: MeshLink) {
        check(crypto.independentlyReviewed && link.bearer != MeshBearer.BLE)
        val envelope = repository.pending().firstOrNull { it.messageId == id } ?: error("MESH_NO_CUSTODY")
        check(repository.attachmentComplete(id)) { "MESH_ATTACHMENT_INCOMPLETE" }
        val count = (envelope.attachmentBytes + limits.chunkBytes - 1) / limits.chunkBytes
        for (index in 0 until count) link.send(MeshPacketWire.encode(MeshPacket.Chunk(id, index, repository.chunk(id, index) ?: error("MESH_ATTACHMENT_EXPIRED"))))
    }
}

/** Explicit foreground opt-in. Cancels scanning, sessions and links on stop; no hidden autostart. */
class MeshCoordinator(private val scope: CoroutineScope, private val repository: MeshRepository, private val crypto: MeshCryptoSuite = UnavailableMeshCrypto()) {
    private val mutableState = MutableStateFlow(MeshRuntimeState())
    val state: StateFlow<MeshRuntimeState> = mutableState.asStateFlow()
    private val router = MeshRouter(repository, crypto)
    private var transport: MeshDiscoveryTransport? = null
    private var discovering: Job? = null
    private var rotation: Job? = null
    private val links = ConcurrentHashMap<String, MeshLink>()
    private val linkBudget = MeshLinkBudget()
    private val incomingJobs = ConcurrentHashMap<String, Job>()
    private val lifecycleLock = Mutex()
    private var ingressWindow = 0L
    private var ingressCount = 0
    private val ingressLock = Mutex()
    suspend fun start(selected: MeshDiscoveryTransport) = lifecycleLock.withLock {
        stopInternal()
        transport = selected
        mutableState.value = MeshRuntimeState(enabled = true, detail = "Activando radios…", cryptoVerified = crypto.independentlyReviewed)
        discovering = scope.launch {
            selected.discoveredPeers.collect { peer ->
                repository.observe(peer)
                mutableState.update { current ->
                    val recent = current.peers.filter { System.currentTimeMillis() - it.seenAt < 60_000 && !(it.rotatingId == peer.rotatingId && it.bearer == peer.bearer) }
                    current.copy(peers = (recent + peer).takeLast(64))
                }
            }
        }
        try {
            selected.startAdvertising(UUID.randomUUID()); selected.startScanning()
            mutableState.update { it.copy(detail = if (crypto.independentlyReviewed) "Descubrimiento activo; entrega requiere acuse autenticado" else "Descubrimiento activo. Mensajes y relevo bloqueados: proveedor criptográfico pendiente de revisión") }
            rotation = scope.launch {
                while (isActive) { delay(15 * 60_000L); selected.startAdvertising(UUID.randomUUID()); repository.prune() }
            }
        } catch (error: Exception) {
            stopInternal()
            if (error is CancellationException) throw error
            mutableState.value = MeshRuntimeState(detail = "No se pudo activar: ${error.message?.take(100) ?: "revisa permisos y radios"}")
        }
    }
    suspend fun connect(peer: MeshPeerAdvertisement, factory: MeshLinkFactory) {
        check(mutableState.value.enabled)
        attach(factory.connect(peer))
    }
    suspend fun attach(link: MeshLink) {
        if (!mutableState.value.enabled) { link.close(); return }
        val reservation = linkBudget.reserve()
        if (reservation == null) { link.close(); return }
        if (!crypto.independentlyReviewed) {
            reservation.close(); link.close()
            mutableState.update { it.copy(detail = "Radio disponible; contenido bloqueado hasta revisar criptografía y emparejar claves") }
            return
        }
        val authenticatedPeer = try { withTimeout(30_000) { crypto.authenticateLink(link) } }
        catch (e: Exception) { reservation.close(); link.close(); if (e is CancellationException) throw e; mutableState.update { it.copy(detail = "Enlace no autenticado; no se transfirió contenido") }; return }
        if (!mutableState.value.enabled || links.putIfAbsent(link.peerId, link) != null) { reservation.close(); link.close(); return }
        mutableState.update { it.copy(links = links.size) }
        incomingJobs[link.peerId] = scope.launch {
            try {
                link.inbound.collect { frame ->
                    if (!admitIngress()) { link.close(); return@collect }
                    val result = router.receive(frame, authenticatedPeer)
                    mutableState.update { it.copy(detail = "Custodia local: ${result.name}. No confirma entrega institucional") }
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; mutableState.update { it.copy(detail = "Enlace detenido: contenido no autenticado o transporte fallido") } }
            finally { reservation.close(); withContext(NonCancellable) { link.close() }; links.remove(link.peerId); incomingJobs.remove(link.peerId); mutableState.update { it.copy(links = links.size) } }
        }
    }
    // Global budget survives reconnects within the opt-in session, bounding Sybil churn before crypto.
    private suspend fun admitIngress(): Boolean = ingressLock.withLock {
        val minute = android.os.SystemClock.elapsedRealtime() / 60_000
        if (minute != ingressWindow) { ingressWindow = minute; ingressCount = 0 }
        ++ingressCount <= 240
    }
    suspend fun stop() = lifecycleLock.withLock { stopInternal() }
    private suspend fun stopInternal() = withContext(NonCancellable) {
        mutableState.value = MeshRuntimeState()
        discovering?.cancelAndJoin(); discovering = null; rotation?.cancelAndJoin(); rotation = null
        incomingJobs.values.toList().forEach { it.cancel() }; incomingJobs.clear()
        links.values.toList().forEach { it.close() }; links.clear()
        transport?.stop(); transport = null
    }
}
