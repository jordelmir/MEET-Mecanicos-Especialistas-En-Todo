package com.elysium369.meet.core.mesh

import android.net.Network
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bounded framed sockets; pending connects share the atomic four-link budget with inbound sockets.
 * Transport setup proves neither content E2EE nor endpoint identity. Only local IPs are accepted.
 */
class AndroidLanTransport(private val scope: CoroutineScope, private val bearer: MeshBearer = MeshBearer.LAN) {
    private var listener: ServerSocket? = null
    private var acceptJob: Job? = null
    private val links = ConcurrentHashMap<String, SocketMeshLink>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val budget = MeshLinkBudget()
    private val lifecycle = Any()
    private var running = true
    private val accepted = Channel<MeshLink>(4)
    val incomingLinks: Flow<MeshLink> = accepted.receiveAsFlow()
    suspend fun listen(bindAddress: InetAddress? = null): Int = withContext(Dispatchers.IO) {
        val socket = synchronized(lifecycle) {
            check(listener == null)
            running = true
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bindAddress, 0)); soTimeout = 1000 }.also { listener = it }
        }
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = try { socket.accept() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: java.io.IOException) { break }
                val lease = budget.reserve()
                if (!local(client.inetAddress) || lease == null) { client.close(); lease?.close(); continue }
                try {
                    synchronized(lifecycle) { check(running); sockets.add(client) }
                    val link = register(client, lease)
                    if (!accepted.trySend(link).isSuccess) link.close()
                } catch (e: Exception) { client.close(); sockets.remove(client); lease.close(); if (e is CancellationException) throw e }
            }
        }
        socket.localPort
    }
    suspend fun connect(address: InetAddress, port: Int, network: Network? = null): MeshLink = withContext(Dispatchers.IO) {
        require(local(address) && port in 1024..65535) { "MESH_LOCAL_ENDPOINT_REQUIRED" }
        val lease = budget.reserve() ?: error("MESH_LINK_CAPACITY")
        var socket: Socket? = null
        try {
            socket = network?.socketFactory?.createSocket() ?: Socket()
            synchronized(lifecycle) { check(running) { "MESH_TRANSPORT_STOPPED" }; sockets.add(socket) }
            socket.connect(InetSocketAddress(address, port), 10_000)
            register(socket, lease)
        } catch (e: Exception) { socket?.close(); sockets.remove(socket); lease.close(); throw e }
    }
    private fun local(address: InetAddress) = address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
    private fun register(socket: Socket, lease: MeshLinkBudget.Lease): SocketMeshLink = synchronized(lifecycle) {
        check(running && !socket.isClosed) { "MESH_TRANSPORT_STOPPED" }
        socket.soTimeout = 30_000; socket.tcpNoDelay = true
        val id = "${socket.remoteSocketAddress}/${UUID.randomUUID()}"
        SocketMeshLink(socket, id, lease).also { links[id] = it; it.start() }
    }
    suspend fun stop() {
        val closing = synchronized(lifecycle) { running = false; listener?.close(); listener = null; sockets.toList() }
        closing.forEach { runCatching { it.close() } }
        acceptJob?.cancelAndJoin(); acceptJob = null
        links.values.toList().forEach { it.close() }; links.clear()
        while (true) { val link = accepted.tryReceive().getOrNull() ?: break; link.close() }
    }
    private inner class SocketMeshLink(private val socket: Socket, override val peerId: String, private val lease: MeshLinkBudget.Lease) : MeshLink {
        override val bearer = this@AndroidLanTransport.bearer
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private val frames = Channel<ByteArray>(4)
        override val inbound: Flow<ByteArray> = frames.receiveAsFlow()
        private val sendLock = Mutex()
        private val readJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                while (isActive) {
                    val size = input.readInt(); require(size in 1..bearer.maxFrameBytes) { "MESH_FRAME_LIMIT" }
                    val bytes = ByteArray(size); input.readFully(bytes)
                    frames.send(bytes)
                }
            } catch (_: java.io.IOException) { } catch (_: IllegalArgumentException) { }
            finally { frames.close(); socket.close(); sockets.remove(socket); links.remove(peerId); lease.close() }
        }
        fun start() { readJob.start() }
        override suspend fun send(frame: ByteArray) = withContext(Dispatchers.IO) {
            require(frame.size in 1..bearer.maxFrameBytes)
            sendLock.withLock {
                // Socket SO_TIMEOUT covers reads only. Close a blocked writer after the deadline.
                val deadline = scope.launch(Dispatchers.IO) { delay(10_000); socket.close() }
                try { output.writeInt(frame.size); output.write(frame); output.flush() } finally { deadline.cancel() }
            }
        }
        override suspend fun close() { socket.close(); readJob.cancel(); frames.cancel(); sockets.remove(socket); links.remove(peerId); lease.close() }
    }
}
