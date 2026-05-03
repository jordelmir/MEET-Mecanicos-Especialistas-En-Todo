package com.elysium369.meet.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * BtClassicTransport — MEET ELITE Performance Edition.
 * Optimized for high-frequency OBD2 polling and robust physical link stability.
 */
class BtClassicTransport(
    var macAddress: String,
    private val bluetoothAdapter: BluetoothAdapter
) : TransportInterface {

    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var socket: BluetoothSocket? = null
    private var inputStream: BufferedInputStream? = null
    private var outputStream: BufferedOutputStream? = null
    private val mutex = Mutex()

    // Cached Reflection Methods for Performance
    private val createRfcommMethod by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("createRfcommSocket", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val createInsecureRfcommMethod by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "═══ BT CONNECT START ═══ MAC=$macAddress")
                val connectStartTime = System.currentTimeMillis()
                
                val device: BluetoothDevice = try {
                    bluetoothAdapter.getRemoteDevice(macAddress)
                } catch (e: Exception) {
                    Log.e(TAG, "✗ MAC inválida: $macAddress", e)
                    throw java.io.IOException("Dirección MAC inválida: $macAddress")
                }
                Log.d(TAG, "Device resolved: name=${device.name}, type=${device.type}, bondState=${device.bondState}")
                
                // 1. HARD RESET — Ensure radio is clean and not searching
                try {
                    if (bluetoothAdapter.isDiscovering) {
                        Log.d(TAG, "Cancelling active BT discovery...")
                        bluetoothAdapter.cancelDiscovery()
                        delay(200)
                    }
                } catch (e: SecurityException) { 
                    Log.w(TAG, "SecurityException on cancelDiscovery", e)
                }
                
                delay(500)

                val connectionMethods = mutableListOf<Pair<String, () -> BluetoothSocket?>>()
                // Prioritize Insecure SPP for ELM327 clones which often fail auth handshake
                connectionMethods.add("Insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) })
                connectionMethods.add("Standard SPP" to { device.createRfcommSocketToServiceRecord(SPP_UUID) })
                connectionMethods.add("Reflection CH1" to { 
                    createRfcommMethod?.invoke(device, 1) as? BluetoothSocket 
                })
                connectionMethods.add("Reflection CH2" to { 
                    createRfcommMethod?.invoke(device, 2) as? BluetoothSocket 
                })

                var lastException: Exception? = null

                for ((methodName, createSocket) in connectionMethods) {
                    val methodStart = System.currentTimeMillis()
                    Log.i(TAG, "→ Trying method: $methodName")
                    try {
                        cleanup()
                        delay(200) // Brief pause before creating socket
                        socket = createSocket()
                        if (socket == null) {
                            Log.w(TAG, "  ✗ $methodName returned null socket, skipping")
                            continue
                        }
                        
                        Log.d(TAG, "  Socket created, attempting connect natively...")
                        // Removed withTimeout because socket.connect() is blocking and uninterruptible
                        // If it fails, it will natively throw an IOException within 12s
                        socket?.connect()
                        
                        inputStream = BufferedInputStream(socket?.inputStream, 32768)
                        outputStream = BufferedOutputStream(socket?.outputStream, 1024)
                        
                        if (isConnected) {
                            val elapsed = System.currentTimeMillis() - methodStart
                            Log.i(TAG, "  ✓ $methodName CONNECTED in ${elapsed}ms")
                            Log.i(TAG, "═══ BT CONNECT SUCCESS ═══ Total: ${System.currentTimeMillis() - connectStartTime}ms")
                            return@withContext
                        } else {
                            Log.w(TAG, "  ✗ $methodName socket.connect() returned but isConnected=false")
                        }
                    } catch (e: Exception) {
                        val elapsed = System.currentTimeMillis() - methodStart
                        Log.w(TAG, "  ✗ $methodName FAILED in ${elapsed}ms: ${e.javaClass.simpleName}: ${e.message}")
                        lastException = e
                        cleanup()
                        delay(500) // Delay before trying next method
                    }
                }
                
                Log.e(TAG, "═══ BT CONNECT FAILED ═══ All methods exhausted. Total: ${System.currentTimeMillis() - connectStartTime}ms")
                // Format error nicely for UI if it's the classic socket read failed error
                val errMsg = lastException?.message ?: ""
                if (errMsg.contains("read failed, socket might closed") || errMsg.contains("timeout")) {
                    throw java.io.IOException("No se pudo enlazar al ELM327. Desvincula el dispositivo en los ajustes de Bluetooth de Android y vuelve a emparejarlo.")
                }
                throw lastException ?: java.io.IOException("ELITE LINK FAILURE: El adaptador no respondió a ninguna estrategia de enlace.")
            }
        }
    }

    override suspend fun reconnect() {
        disconnect()
        delay(1000)
        connect()
    }

    private fun cleanup() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        socket = null
        inputStream = null
        outputStream = null
    }

    override suspend fun disconnect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                cleanup()
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val out = outputStream ?: throw java.io.IOException("Socket Error: Enlace no disponible")
                try {
                    out.write(data)
                    out.flush()
                } catch (e: Exception) {
                    // Check if socket is still alive
                    if (socket?.isConnected != true) {
                        throw java.io.IOException("Broken Pipe: El adaptador cerró la conexión.")
                    }
                    throw java.io.IOException("Send Failure: ${e.message}")
                }
            }
        }
    }

    private val readBuffer = ByteArray(8192) // Larger buffer for heavy duty logs

    override suspend fun read(maxBytes: Int): ByteArray? {
        return withContext(Dispatchers.IO) {
            val stream = inputStream ?: return@withContext null
            try {
                // ELITE ADAPTIVE READ: Longer timeout for clone adapter compatibility
                var totalWaited = 0
                val timeoutLimit = 600 // 600ms to accommodate slow clone responses
                val pollInterval = 5L  // 5ms polling for good latency balance

                while (totalWaited < timeoutLimit) {
                    val available = stream.available()
                    if (available > 0) {
                        val toRead = minOf(available, maxBytes, readBuffer.size)
                        val bytesRead = stream.read(readBuffer, 0, toRead)
                        if (bytesRead > 0) return@withContext readBuffer.copyOf(bytesRead)
                    }
                    delay(pollInterval)
                    totalWaited += pollInterval.toInt()
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Purges the input stream to ensure no residual data corrupts the next command response.
     * Essential for high-frequency PID polling.
     */
    suspend fun drain() {
        withContext(Dispatchers.IO) {
            val stream = inputStream ?: return@withContext
            try {
                var available = stream.available()
                while (available > 0) {
                    stream.skip(available.toLong())
                    delay(1)
                    available = stream.available()
                }
            } catch (_: Exception) {}
        }
    }

    override val isConnected: Boolean
        get() = socket?.isConnected == true && inputStream != null && outputStream != null
    
    companion object {
        private const val TAG = "MEET_BT"
    }
}
