package com.elysium369.meet.communications

import android.content.Context
import android.util.Base64
import android.util.Log
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RideCallSignal(
    val type: String, // JOIN, LEAVE, MUTE, PING, PONG
    val rideId: String,
    val senderRole: String, // DRIVER, PASSENGER
    val timestamp: Long = System.currentTimeMillis(),
    val isMuted: Boolean = false,
)

@Serializable
data class RideCallAudioChunk(
    val rideId: String,
    val senderRole: String,
    val seq: Long,
    val data: String, // Base64 encoded 16kHz PCM
    val timestamp: Long = System.currentTimeMillis(),
)

sealed interface LiveCallState {
    data object Idle : LiveCallState
    data class Connecting(val rideId: String, val role: String) : LiveCallState
    data class Incoming(val rideId: String, val callerRole: String) : LiveCallState
    data class Active(
        val rideId: String,
        val role: String,
        val startedAtEpochMs: Long = System.currentTimeMillis(),
        val isMuted: Boolean = false,
        val peerConnected: Boolean = false,
        val transport: String = "DUAL (Cloud + Red Mesh Local)",
    ) : LiveCallState
    data class Ended(val reason: String) : LiveCallState
    data class Error(val message: String) : LiveCallState
}

/**
 * RideLiveVoiceBridge — Real-time bidirectional voice link between driver and passenger.
 *
 * Implements dual-transport architecture:
 * 1. Cloud: Supabase Realtime broadcast channel (`call_ride_$rideId`)
 * 2. Mesh: Local UDP broadcast on port 42424 (`255.255.255.255`) for 0ms offline/local Wi-Fi latency
 *
 * Captures 16kHz PCM mono with hardware AEC/NS/AGC and streams directly with de-duplication.
 */
@Singleton
class RideLiveVoiceBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEngine: RealtimeLiveAudioEngine,
) {
    companion object {
        private const val TAG = "RideLiveVoiceBridge"
        const val UDP_PORT = 42424
        const val UDP_PORT_FALLBACK = 42425
        private const val MAGIC_0: Byte = 0x45 // 'E'
        private const val MAGIC_1: Byte = 0x56 // 'V'
        private const val TYPE_JOIN: Byte = 0x01
        private const val TYPE_AUDIO: Byte = 0x02
        private const val TYPE_LEAVE: Byte = 0x03
        private const val TYPE_PING: Byte = 0x04
        private const val ROLE_DRIVER: Byte = 0x01
        private const val ROLE_PASSENGER: Byte = 0x02
    }

    private val mutex = Mutex()
    private val _callState = MutableStateFlow<LiveCallState>(LiveCallState.Idle)
    val callState: StateFlow<LiveCallState> = _callState.asStateFlow()

    private var callScope: CoroutineScope? = null
    private var realtimeChannel: RealtimeChannel? = null
    private var udpSocket: DatagramSocket? = null
    private var activeRideId: String? = null
    private var myRole: String? = null
    private val sequenceCounter = AtomicLong(0L)

    private var passiveScope: CoroutineScope? = null
    private var passiveChannel: RealtimeChannel? = null
    private var observingRideId: String? = null

    // De-duplication cache: LRU-style set of recently received sequence numbers
    private val processedSequences = Collections.newSetFromMap(
        object : java.util.LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>?): Boolean {
                return size > 256
            }
        },
    )
    private val sequenceLock = Any()

    fun startObservingCalls(rideId: String, role: String) {
        if (observingRideId == rideId && passiveScope?.isActive == true) return
        stopObservingCalls()
        observingRideId = rideId
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        passiveScope = scope

        val normalizedRole = if (role.startsWith("driver", ignoreCase = true)) "DRIVER" else "PASSENGER"

        scope.launch {
            try {
                val channelName = "call_ride_$rideId"
                val channel = SupabaseModule.client.channel(channelName)
                passiveChannel = channel

                launch {
                    channel.broadcastFlow<RideCallSignal>("signal").collect { signal ->
                        if (signal.rideId == rideId && signal.senderRole != normalizedRole) {
                            when (signal.type) {
                                "JOIN", "PING" -> {
                                    val current = _callState.value
                                    if (current is LiveCallState.Active) {
                                        _callState.update { (it as LiveCallState.Active).copy(peerConnected = true) }
                                    } else if (current is LiveCallState.Idle || current is LiveCallState.Ended) {
                                        Log.i(TAG, "Incoming call detected for ride $rideId from ${signal.senderRole}")
                                        _callState.value = LiveCallState.Incoming(rideId, signal.senderRole)
                                    }
                                }
                                "PONG" -> {
                                    _callState.update { current ->
                                        if (current is LiveCallState.Active) current.copy(peerConnected = true)
                                        else current
                                    }
                                }
                                "LEAVE" -> {
                                    val current = _callState.value
                                    if (current is LiveCallState.Incoming) {
                                        _callState.value = LiveCallState.Ended("PEER_CANCELLED")
                                        delay(2000)
                                        _callState.value = LiveCallState.Idle
                                    }
                                }
                            }
                        }
                    }
                }
                channel.subscribe()
                Log.i(TAG, "Observing incoming call signals for ride $rideId as $normalizedRole")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to observe call signals for ride $rideId", e)
            }
        }
    }

    fun stopObservingCalls() {
        passiveScope?.cancel()
        passiveScope = null
        passiveChannel = null
        observingRideId = null
    }

    suspend fun startCall(rideId: String, role: String): Result<Unit> = mutex.withLock {
        if (_callState.value is LiveCallState.Active && activeRideId == rideId) {
            return Result.success(Unit) // Already in call
        }

        val normalizedRole = if (role.startsWith("driver", ignoreCase = true)) "DRIVER" else "PASSENGER"
        activeRideId = rideId
        myRole = normalizedRole
        _callState.value = LiveCallState.Connecting(rideId, normalizedRole)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        callScope = scope

        return runCatching {
            // 1. Initialize UDP Mesh Socket
            val socket = createUdpSocket()
            udpSocket = socket

            // 2. Start UDP Receiver loop
            startUdpReceiver(socket, rideId, role, scope)

            // 3. Connect Supabase Realtime Channel
            val channelName = "call_ride_$rideId"
            val channel = SupabaseModule.client.channel(channelName)
            realtimeChannel = channel

            // Listen for signaling
            scope.launch {
                channel.broadcastFlow<RideCallSignal>("signal").collect { signal ->
                    if (signal.rideId == rideId && signal.senderRole != role) {
                        when (signal.type) {
                            "JOIN" -> {
                                Log.i(TAG, "Peer joined the call via Cloud: ${signal.senderRole}")
                                _callState.update { current ->
                                    if (current is LiveCallState.Active) {
                                        current.copy(peerConnected = true)
                                    } else current
                                }
                                // Reply with PONG so peer knows we are already here
                                sendSignal(channel, socket, rideId, role, "PONG")
                            }
                            "PONG" -> {
                                _callState.update { current ->
                                    if (current is LiveCallState.Active) {
                                        current.copy(peerConnected = true)
                                    } else current
                                }
                            }
                            "LEAVE" -> {
                                Log.i(TAG, "Peer ended the call: ${signal.senderRole}")
                                endCallInternal("PEER_HANGUP")
                            }
                            "MUTE" -> {
                                Log.i(TAG, "Peer mute status: ${signal.isMuted}")
                            }
                        }
                    }
                }
            }

            // Listen for Cloud Audio Chunks
            scope.launch {
                channel.broadcastFlow<RideCallAudioChunk>("audio_chunk").collect { chunk ->
                    if (chunk.rideId == rideId && chunk.senderRole != role) {
                        val isNew = synchronized(sequenceLock) {
                            processedSequences.add(chunk.seq)
                        }
                        if (isNew) {
                            runCatching {
                                val pcmBytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                                audioEngine.playAudioChunk(pcmBytes)
                            }
                        }
                    }
                }
            }

            channel.subscribe()

            // 4. Start Hardware Audio Engine
            val audioStarted = audioEngine.start(scope) { chunkBytes ->
                val seq = sequenceCounter.incrementAndGet()
                // Broadcast chunk over Cloud Realtime
                scope.launch {
                    runCatching {
                        val base64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
                        channel.broadcast(
                            event = "audio_chunk",
                            message = RideCallAudioChunk(
                                rideId = rideId,
                                senderRole = normalizedRole,
                                seq = seq,
                                data = base64,
                            ),
                        )
                    }
                }
                // Broadcast chunk over Local UDP Mesh (0ms local latency)
                sendUdpPacket(socket, TYPE_AUDIO, rideId, normalizedRole, seq, chunkBytes)
            }

            if (!audioStarted) {
                error("No fue posible inicializar el subsistema de audio de llamadas.")
            }

            // Announce presence via Cloud and Mesh immediately and periodically until connected
            sendSignal(channel, socket, rideId, normalizedRole, "JOIN")
            sendUdpPacket(socket, TYPE_JOIN, rideId, normalizedRole, 0L, ByteArray(0))
            scope.launch {
                while (isActive) {
                    delay(1200)
                    val current = _callState.value
                    if (current is LiveCallState.Active && !current.peerConnected) {
                        sendSignal(channel, socket, rideId, normalizedRole, "JOIN")
                        sendUdpPacket(socket, TYPE_JOIN, rideId, normalizedRole, 0L, ByteArray(0))
                    } else if (current !is LiveCallState.Active) {
                        break
                    }
                }
            }

            _callState.value = LiveCallState.Active(
                rideId = rideId,
                role = normalizedRole,
                startedAtEpochMs = System.currentTimeMillis(),
                isMuted = false,
                peerConnected = false,
            )
            Log.i(TAG, "Live call started successfully for ride $rideId as $normalizedRole")
            Unit
        }.onFailure { err ->
            Log.e(TAG, "Failed to start live call", err)
            endCallInternal("ERROR: ${err.message}")
            _callState.value = LiveCallState.Error(err.message ?: "Error al conectar llamada")
        }
    }

    fun toggleMute(): Boolean {
        val current = _callState.value
        if (current !is LiveCallState.Active) return false
        val newMute = !current.isMuted
        audioEngine.setMuted(newMute)
        _callState.value = current.copy(isMuted = newMute)

        callScope?.launch {
            val channel = realtimeChannel
            val socket = udpSocket
            val rideId = activeRideId ?: return@launch
            val role = myRole ?: return@launch
            if (channel != null && socket != null) {
                runCatching {
                    channel.broadcast(
                        event = "signal",
                        message = RideCallSignal(
                            type = "MUTE",
                            rideId = rideId,
                            senderRole = role,
                            isMuted = newMute,
                        ),
                    )
                }
            }
        }
        return newMute
    }

    suspend fun endCall(reason: String = "USER_HANGUP") = mutex.withLock {
        endCallInternal(reason)
    }

    private fun endCallInternal(reason: String) {
        val currentRide = activeRideId
        val currentRole = myRole
        val channel = realtimeChannel
        val socket = udpSocket
        val oldScope = callScope

        activeRideId = null
        myRole = null
        realtimeChannel = null
        udpSocket = null
        callScope = null

        runCatching {
            _callState.value = LiveCallState.Ended(reason)
            Log.i(TAG, "Live call ended: $reason")
        }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            if (currentRide != null && currentRole != null) {
                runCatching {
                    channel?.broadcast(
                        event = "signal",
                        message = RideCallSignal(
                            type = "LEAVE",
                            rideId = currentRide,
                            senderRole = currentRole,
                        ),
                    )
                }
                runCatching {
                    socket?.let { s ->
                        sendUdpPacket(s, TYPE_LEAVE, currentRide, currentRole, 0L, ByteArray(0))
                    }
                }
            }

            runCatching { audioEngine.stop() }
            runCatching { channel?.unsubscribe() }
            runCatching { socket?.close() }
            runCatching { oldScope?.cancel() }
            
            synchronized(sequenceLock) { processedSequences.clear() }
            
            withContext(Dispatchers.Main) {
                delay(2500)
                if (_callState.value is LiveCallState.Ended) {
                    _callState.value = LiveCallState.Idle
                }
            }
        }
    }

    private fun createUdpSocket(): DatagramSocket {
        return runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(UDP_PORT))
            }
        }.getOrElse {
            // Fallback port
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(UDP_PORT_FALLBACK))
            }
        }
    }

    private fun startUdpReceiver(
        socket: DatagramSocket,
        targetRideId: String,
        localRole: String,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)
            val expectedHash = targetRideId.hashCode()

            while (isActive && !socket.isClosed) {
                try {
                    socket.receive(packet)
                    if (packet.length < 20) continue // Minimum header size

                    val byteBuffer = ByteBuffer.wrap(packet.data, 0, packet.length)
                    val m0 = byteBuffer.get()
                    val m1 = byteBuffer.get()
                    if (m0 != MAGIC_0 || m1 != MAGIC_1) continue

                    val type = byteBuffer.get()
                    val rideHash = byteBuffer.getInt()
                    val roleByte = byteBuffer.get()
                    val seq = byteBuffer.getLong()
                    val payloadLen = byteBuffer.getInt()

                    if (rideHash != expectedHash) continue

                    val incomingRole = if (roleByte == ROLE_DRIVER) "DRIVER" else "PASSENGER"
                    if (incomingRole == localRole) continue // Ignore own broadcast

                    when (type) {
                        TYPE_JOIN, TYPE_PING -> {
                            Log.i(TAG, "Peer detected via UDP Mesh: $incomingRole")
                            _callState.update { current ->
                                if (current is LiveCallState.Active) {
                                    current.copy(peerConnected = true, transport = "Red Mesh Local Directa")
                                } else current
                            }
                        }
                        TYPE_AUDIO -> {
                            if (payloadLen > 0 && byteBuffer.remaining() >= payloadLen) {
                                val isNew = synchronized(sequenceLock) {
                                    processedSequences.add(seq)
                                }
                                if (isNew) {
                                    val audioData = ByteArray(payloadLen)
                                    byteBuffer.get(audioData)
                                    audioEngine.playAudioChunk(audioData)
                                }
                            }
                        }
                        TYPE_LEAVE -> {
                            Log.i(TAG, "Peer left via UDP Mesh")
                            withContext(Dispatchers.Main) {
                                endCall("PEER_HANGUP_MESH")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
    }

    private fun sendUdpPacket(
        socket: DatagramSocket,
        type: Byte,
        rideId: String,
        role: String,
        seq: Long,
        payload: ByteArray,
    ) {
        runCatching {
            val totalSize = 2 + 1 + 4 + 1 + 8 + 4 + payload.size
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.put(MAGIC_0)
            buffer.put(MAGIC_1)
            buffer.put(type)
            buffer.putInt(rideId.hashCode())
            buffer.put(if (role == "DRIVER") ROLE_DRIVER else ROLE_PASSENGER)
            buffer.putLong(seq)
            buffer.putInt(payload.size)
            if (payload.isNotEmpty()) {
                buffer.put(payload)
            }

            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val datagram = DatagramPacket(buffer.array(), totalSize, broadcastAddr, UDP_PORT)
            socket.send(datagram)
        }
    }

    private suspend fun sendSignal(
        channel: RealtimeChannel,
        socket: DatagramSocket,
        rideId: String,
        role: String,
        type: String,
    ) {
        runCatching {
            channel.broadcast(
                event = "signal",
                message = RideCallSignal(
                    type = type,
                    rideId = rideId,
                    senderRole = role,
                ),
            )
        }
        val udpType = when (type) {
            "JOIN" -> TYPE_JOIN
            "LEAVE" -> TYPE_LEAVE
            else -> TYPE_PING
        }
        sendUdpPacket(socket, udpType, rideId, role, 0L, ByteArray(0))
    }
}
