package com.elysium369.meet.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.elysium369.meet.core.obd.ConnectMethod
import com.elysium369.meet.core.obd.KnownGoodAdapterStore
import com.elysium369.meet.core.obd.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * BtClassicTransport — Elysium Vanguard Performance Edition.
 * Optimized for high-frequency OBD2 polling and robust physical link stability.
 */
class BtClassicTransport(
    var macAddress: String,
    private val bluetoothAdapter: BluetoothAdapter
) : TransportInterface {

    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var socket: BluetoothSocket? = null
    private var rawInputStream: InputStream? = null
    private var rawOutputStream: OutputStream? = null
    private val mutex = Mutex()

    // Continuous In-Memory Receive Buffer
    private val rxBuffer = java.io.ByteArrayOutputStream(8192)
    private val rxMutex = Any()
    private var readerJob: kotlinx.coroutines.Job? = null

    // Cached Reflection Methods for Performance
    private val createRfcommMethod by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("createRfcommSocket", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val createInsecureRfcommMethod by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType) }.getOrNull()
    }

    private fun invokeReflectiveSocketCreation(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        val method = createRfcommMethod ?: return null
        try {
            return method.invoke(device, channel) as? BluetoothSocket
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            if (cause is SecurityException) {
                throw SecurityException("Permiso de conexión Bluetooth denegado en la llamada de reflexión: ${cause.message}", cause)
            }
            throw java.io.IOException("Error en creación de socket por reflexión: ${cause?.message}", cause)
        } catch (e: Exception) {
            if (e is SecurityException) {
                throw SecurityException("Permiso de conexión Bluetooth denegado en la llamada de reflexión: ${e.message}", e)
            }
            throw java.io.IOException("Fallo reflexivo: ${e.message}", e)
        }
    }

    private fun invokeReflectiveInsecureSocketCreation(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        val method = createInsecureRfcommMethod ?: return null
        try {
            return method.invoke(device, channel) as? BluetoothSocket
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            if (cause is SecurityException) {
                throw SecurityException("Permiso de conexión Bluetooth denegado en la llamada de reflexión: ${cause.message}", cause)
            }
            throw java.io.IOException("Error en creación de socket inseguro por reflexión: ${cause?.message}", cause)
        } catch (e: Exception) {
            if (e is SecurityException) {
                throw SecurityException("Permiso de conexión Bluetooth denegado en la llamada de reflexión: ${e.message}", e)
            }
            throw java.io.IOException("Fallo reflexivo inseguro: ${e.message}", e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "═══ BT CONNECT START ═══ MAC=$macAddress")
                val connectStartTime = System.currentTimeMillis()
                
                val device: BluetoothDevice = try {
                    bluetoothAdapter.getRemoteDevice(macAddress)
                } catch (e: SecurityException) {
                    Log.e(TAG, "✗ Permiso de conexión Bluetooth denegado (Android 12+)", e)
                    throw java.io.IOException("Falta el permiso de conexión Bluetooth (BLUETOOTH_CONNECT). Otórgalo en los ajustes del sistema.")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ MAC inválida: $macAddress", e)
                    throw java.io.IOException("Dirección MAC inválida: $macAddress")
                }
                
                val deviceName = runCatching { device.name }.getOrDefault("Unknown")
                val deviceType = runCatching { device.type }.getOrDefault(0)
                val bondState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                
                Log.d(TAG, "Device resolved: name=$deviceName, type=$deviceType, bondState=$bondState")
                
                // 1. HARD RESET — Ensure radio is clean and not searching. Always cancel discovery prior to connecting.
                try {
                    bluetoothAdapter.cancelDiscovery()
                    delay(150)
                } catch (e: Exception) { 
                    Log.w(TAG, "Error cancelling BT discovery", e)
                }
                
                delay(200)

                val connectionMethods = mutableListOf<Pair<String, () -> BluetoothSocket?>>()
                // Direct RFCOMM Channel 1 bypasses slow remote SDP queries on cheap ELM clones
                connectionMethods.add("Insecure Reflection CH1" to { invokeReflectiveInsecureSocketCreation(device, 1) })
                connectionMethods.add("Reflection CH1" to { invokeReflectiveSocketCreation(device, 1) })
                connectionMethods.add("Insecure SPP" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) })
                connectionMethods.add("Standard SPP" to { device.createRfcommSocketToServiceRecord(SPP_UUID) })
                connectionMethods.add("Insecure Reflection CH2" to { invokeReflectiveInsecureSocketCreation(device, 2) })
                connectionMethods.add("Reflection CH2" to { invokeReflectiveSocketCreation(device, 2) })

                // Prioritize known successful connect method for this device if previously recorded
                val knownProfile = KnownGoodAdapterStore.getProfile(macAddress)
                val preferredName = when (knownProfile?.preferredConnectMethod) {
                    ConnectMethod.REFLECTION_CH1 -> "Insecure Reflection CH1"
                    ConnectMethod.INSECURE_SPP -> "Insecure SPP"
                    ConnectMethod.STANDARD_SPP -> "Standard SPP"
                    ConnectMethod.REFLECTION_CH2 -> "Insecure Reflection CH2"
                    else -> null
                }
                if (preferredName != null) {
                    val idx = connectionMethods.indexOfFirst { it.first.equals(preferredName, ignoreCase = true) }
                    if (idx > 0) {
                        val item = connectionMethods.removeAt(idx)
                        connectionMethods.add(0, item)
                        Log.i(TAG, "⚡ Prioritizing known successful connect method: ${item.first}")
                    }
                }

                var lastException: Exception? = null

                for ((methodName, createSocket) in connectionMethods) {
                    val methodStart = System.currentTimeMillis()
                    Log.i(TAG, "→ Trying method: $methodName")
                    try {
                        cleanup()
                        delay(60)
                        
                        socket = try {
                            createSocket()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "  ✗ SecurityException on socket creation: ${e.message}")
                            null
                        } catch (e: Exception) {
                            Log.e(TAG, "  ✗ Error creating socket: ${e.message}")
                            null
                        }

                        if (socket == null) {
                            Log.w(TAG, "  ✗ $methodName returned null socket, skipping")
                            continue
                        }
                        
                        Log.d(TAG, "  Socket created, attempting connect natively (active watchdog 7500ms)...")
                        // Always ensure discovery is cancelled immediately before the blocking connect call
                        runCatching { bluetoothAdapter.cancelDiscovery() }

                        val currentSocket = socket ?: continue
                        val watchdogJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            delay(7500L)
                            if (!currentSocket.isConnected) {
                                Log.w(TAG, "  ✗ Active watchdog triggered at 7500ms; forcibly unblocking connect() via socket.close()")
                                runCatching { currentSocket.close() }
                            }
                        }

                        try {
                            currentSocket.connect()
                        } catch (e: Exception) {
                            if (System.currentTimeMillis() - methodStart >= 7300L) {
                                throw TransportConnectTimeout("Bluetooth connect watchdog timeout on method: $methodName")
                            }
                            throw e
                        } finally {
                            watchdogJob.cancel()
                        }
                        
                        rawInputStream = socket?.inputStream
                        rawOutputStream = socket?.outputStream
                        
                        if (isConnected) {
                            val elapsed = System.currentTimeMillis() - methodStart
                            Log.i(TAG, "  ✓ $methodName CONNECTED in ${elapsed}ms")
                            Log.i(TAG, "═══ BT CONNECT SUCCESS ═══ Total: ${System.currentTimeMillis() - connectStartTime}ms")
                            
                            val methodEnum = when {
                                methodName.contains("CH1", true) -> ConnectMethod.REFLECTION_CH1
                                methodName.contains("Insecure SPP", true) -> ConnectMethod.INSECURE_SPP
                                methodName.contains("Standard SPP", true) -> ConnectMethod.STANDARD_SPP
                                methodName.contains("CH2", true) -> ConnectMethod.REFLECTION_CH2
                                else -> ConnectMethod.REFLECTION_CH1
                            }
                            try {
                                KnownGoodAdapterStore.recordSuccess(
                                    fingerprint = macAddress,
                                    transportType = TransportType.BLUETOOTH_CLASSIC,
                                    connectMethod = methodEnum,
                                    protocol = knownProfile?.preferredProtocol,
                                    connectDurationMs = elapsed
                                )
                            } catch (_: Exception) {}

                            // Start Continuous Reader Worker Thread
                            startReaderWorker()
                            return@withContext
                        } else {
                            Log.w(TAG, "  ✗ $methodName socket.connect() returned but isConnected=false")
                            cleanup()
                        }
                    } catch (e: Exception) {
                        if (e is SecurityException) {
                            Log.e(TAG, "✗ Permiso de conexión Bluetooth denegado durante el enlace", e)
                            throw java.io.IOException("Falta el permiso de conexión Bluetooth (BLUETOOTH_CONNECT). Otórgalo en los ajustes del sistema.")
                        }
                        val elapsed = System.currentTimeMillis() - methodStart
                        Log.w(TAG, "  ✗ $methodName FAILED in ${elapsed}ms: ${e.javaClass.simpleName}: ${e.message}")
                        lastException = e
                        cleanup()
                        delay(200)
                    }
                }
                
                Log.e(TAG, "═══ BT CONNECT FAILED ═══ All methods exhausted. Total: ${System.currentTimeMillis() - connectStartTime}ms")
                // Format error nicely for UI if it's the classic socket read failed error
                val errMsg = lastException?.message ?: ""
                if (errMsg.contains("read failed, socket might closed") || errMsg.contains("timeout")) {
                    throw java.io.IOException("No se pudo enlazar al ELM327. Verifica que el adaptador tenga alimentación y que el Bluetooth esté encendido.")
                }
                throw lastException ?: java.io.IOException("ELITE LINK FAILURE: El adaptador no respondió a ninguna estrategia de enlace.")
            }
        }
    }

    private fun startReaderWorker() {
        readerJob?.cancel()
        synchronized(rxMutex) {
            rxBuffer.reset()
        }
        val stream = rawInputStream ?: return
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val buf = ByteArray(2048)
            try {
                while (socket?.isConnected == true) {
                    val bytesRead = stream.read(buf)
                    if (bytesRead > 0) {
                        synchronized(rxMutex) {
                            if (rxBuffer.size() + bytesRead > MAX_RX_BUFFER_SIZE) {
                                Log.w(TAG, "Bluetooth Classic rxBuffer bounded overflow protection triggered; resetting buffer")
                                rxBuffer.reset()
                            }
                            rxBuffer.write(buf, 0, bytesRead)
                        }
                    } else if (bytesRead < 0) {
                        break
                    }
                }
            } catch (_: Exception) {
                // Expected when socket is closed on disconnect
            }
        }
    }

    override suspend fun reconnect() {
        disconnect()
        delay(500)
        connect()
    }

    private fun cleanup() {
        readerJob?.cancel()
        readerJob = null
        runCatching { rawOutputStream?.flush() }
        runCatching { rawInputStream?.close() }
        runCatching { rawOutputStream?.close() }
        runCatching { socket?.close() }
        socket = null
        rawInputStream = null
        rawOutputStream = null
        synchronized(rxMutex) {
            rxBuffer.reset()
        }
    }

    override suspend fun disconnect() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                cleanup()
                delay(120)
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val out = rawOutputStream ?: throw java.io.IOException("Socket Error: Enlace no disponible")
                try {
                    out.write(data)
                    out.flush()
                } catch (e: Exception) {
                    if (socket?.isConnected != true) {
                        cleanup()
                        throw TransportRemoteClosed("Broken Pipe: El adaptador cerró la conexión.")
                    }
                    throw TransportWriteFailure("Send Failure: ${e.message}", e)
                }
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            synchronized(rxMutex) {
                val current = rxBuffer.toByteArray()
                if (current.isNotEmpty()) {
                    val hasPrompt = current.contains('>'.code.toByte())
                    if (current.size >= maxBytes || hasPrompt) {
                        rxBuffer.reset()
                        return current
                    }
                }
            }
            delay(3)
        }
        synchronized(rxMutex) {
            if (rxBuffer.size() > 0) {
                val res = rxBuffer.toByteArray()
                rxBuffer.reset()
                return res
            }
        }
        return null
    }

    /**
     * Purges the input stream to ensure no residual data corrupts the next command response.
     * Essential for high-frequency PID polling.
     */
    override suspend fun drain() {
        synchronized(rxMutex) {
            rxBuffer.reset()
        }
    }

    override val isConnected: Boolean
        get() = socket?.isConnected == true && rawInputStream != null && rawOutputStream != null
    
    companion object {
        private const val TAG = "EV_BT"
        private const val MAX_RX_BUFFER_SIZE = 65536
    }
}
