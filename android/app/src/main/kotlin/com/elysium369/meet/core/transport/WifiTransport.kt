package com.elysium369.meet.core.transport

import com.elysium369.meet.core.obd.TransportLinkEvent
import com.elysium369.meet.core.obd.TransportLinkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiTransport(
    private val ipAddress: String = "192.168.0.10",
    private val port: Int = 35000
) : TransportInterface {

    private val _linkState = MutableStateFlow<TransportLinkState>(TransportLinkState.Disconnected)
    override val linkState: StateFlow<TransportLinkState> = _linkState.asStateFlow()

    private val _linkEvents = MutableSharedFlow<TransportLinkEvent>(extraBufferCapacity = 16)
    override val linkEvents: SharedFlow<TransportLinkEvent> = _linkEvents.asSharedFlow()

    @Volatile private var socket: Socket? = null
    @Volatile private var connectingSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val rxBuffer = java.io.ByteArrayOutputStream(8192)
    private val rxMutex = Any()
    private var readerJob: Job? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            _linkState.value = TransportLinkState.Connecting
            _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connecting))
            var tempSocket: Socket? = null
            try {
                // OBD adapters are local direct endpoints; never route them through a system SOCKS proxy.
                tempSocket = Socket(java.net.Proxy.NO_PROXY)
                connectingSocket = tempSocket
                tempSocket.apply {
                    // Keepalive a nivel TCP — el sistema operativo mantiene el socket vivo
                    setPerformancePreferences(0, 1, 0) // priorizar latencia sobre bandwidth
                    soTimeout = 0         // continuous reader owns the blocking read
                    tcpNoDelay = true     // enviar bytes inmediatamente, sin Nagle algorithm
                    keepAlive = true      // TCP keepalive del SO
                    receiveBufferSize = 4096
                    sendBufferSize = 256  // comandos OBD son cortos
                    connect(InetSocketAddress(ipAddress, port), 5000)
                }
                socket = tempSocket
                connectingSocket = null
                inputStream = tempSocket.getInputStream()
                outputStream = tempSocket.getOutputStream()
                startReaderWorker()
                _linkState.value = TransportLinkState.Connected
                _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Connected))
            } catch (e: Exception) {
                tempSocket?.close()
                connectingSocket = null
                socket = null
                inputStream = null
                outputStream = null
                _linkState.value = TransportLinkState.IoFailure(e)
                _linkEvents.tryEmit(TransportLinkEvent.IoFailure(e, System.nanoTime() / 1_000_000L))
                throw e
            }
        }
    }

    override fun abortConnect() {
        readerJob?.cancel()
        runCatching { connectingSocket?.close() }
        runCatching { socket?.close() }
        connectingSocket = null
        _linkState.value = TransportLinkState.Disconnected
        _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Disconnected))
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            _linkState.value = TransportLinkState.Closing
            readerJob?.cancel()
            readerJob = null
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            inputStream = null
            outputStream = null
            synchronized(rxMutex) { rxBuffer.reset() }
            _linkState.value = TransportLinkState.Disconnected
            _linkEvents.tryEmit(TransportLinkEvent.StateChanged(TransportLinkState.Disconnected))
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val out = outputStream ?: run {
                _linkState.value = TransportLinkState.RemoteClosed("WiFi socket no disponible")
                throw java.io.IOException("WiFi socket no disponible")
            }
            try {
                out.write(data)
                out.flush()
                _linkEvents.tryEmit(TransportLinkEvent.BytesSent(data.size, System.nanoTime() / 1_000_000L))
            } catch (e: Exception) {
                _linkState.value = TransportLinkState.IoFailure(e)
                _linkEvents.tryEmit(TransportLinkEvent.IoFailure(e, System.nanoTime() / 1_000_000L))
                throw e
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        val startedAt = System.nanoTime() / 1_000_000L
        while (System.nanoTime() / 1_000_000L - startedAt < timeoutMs) {
            synchronized(rxMutex) {
                val current = rxBuffer.toByteArray()
                if (current.isNotEmpty() && (current.size >= maxBytes || current.contains('>'.code.toByte()))) {
                    rxBuffer.reset()
                    return current
                }
            }
            if (_linkState.value is TransportLinkState.RemoteClosed || _linkState.value is TransportLinkState.IoFailure) return null
            delay(3)
        }
        return synchronized(rxMutex) {
            if (rxBuffer.size() == 0) null else rxBuffer.toByteArray().also { rxBuffer.reset() }
        }
    }

    override suspend fun drain() {
        withContext(Dispatchers.IO) {
            synchronized(rxMutex) { rxBuffer.reset() }
        }
    }

    private fun startReaderWorker() {
        readerJob?.cancel()
        synchronized(rxMutex) { rxBuffer.reset() }
        val stream = inputStream ?: return
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead < 0) {
                        publishRemoteClosed("WiFi stream EOF")
                        break
                    }
                    if (bytesRead > 0) {
                        _linkEvents.tryEmit(TransportLinkEvent.BytesReceived(bytesRead, System.nanoTime() / 1_000_000L))
                        synchronized(rxMutex) {
                            if (rxBuffer.size() + bytesRead > MAX_RX_BUFFER_BYTES) rxBuffer.reset()
                            rxBuffer.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (error: Exception) {
                if (_linkState.value is TransportLinkState.Connected) {
                    publishRemoteClosed("WiFi reader failure: ${error.message}")
                }
            }
        }
    }

    private fun publishRemoteClosed(reason: String) {
        val timestamp = System.nanoTime() / 1_000_000L
        _linkState.value = TransportLinkState.RemoteClosed(reason, timestamp)
        _linkEvents.tryEmit(TransportLinkEvent.RemoteClosed(reason, timestamp))
        runCatching { socket?.close() }
        socket = null
        inputStream = null
        outputStream = null
    }

    override val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } ?: false

    companion object {
        private const val MAX_RX_BUFFER_BYTES = 64 * 1024
    }
}
