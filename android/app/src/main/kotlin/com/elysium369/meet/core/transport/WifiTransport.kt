package com.elysium369.meet.core.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiTransport(
    private val ipAddress: String = "192.168.0.10",
    private val port: Int = 35000
) : TransportInterface {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            socket = Socket().apply {
                // Keepalive a nivel TCP — el sistema operativo mantiene el socket vivo
                setPerformancePreferences(0, 1, 0) // priorizar latencia sobre bandwidth
                soTimeout = 5000      // 5s timeout de lectura
                tcpNoDelay = true     // enviar bytes inmediatamente, sin Nagle algorithm
                keepAlive = true      // TCP keepalive del SO
                receiveBufferSize = 4096
                sendBufferSize = 256  // comandos OBD son cortos
                connect(InetSocketAddress(ipAddress, port), 5000)
            }
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
            socket = null
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            outputStream?.write(data)
            outputStream?.flush()
        }
    }

    override suspend fun read(maxBytes: Int): ByteArray? {
        return withContext(Dispatchers.IO) {
            val stream = inputStream ?: return@withContext null
            if (stream.available() > 0) {
                val buffer = ByteArray(maxBytes)
                val bytesRead = stream.read(buffer)
                if (bytesRead > 0) {
                    buffer.copyOf(bytesRead)
                } else null
            } else {
                null
            }
        }
    }

    override val isConnected: Boolean
        get() = socket?.isConnected == true
}
