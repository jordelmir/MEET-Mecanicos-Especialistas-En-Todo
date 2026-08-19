package com.elysium369.meet.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * BleTransport — Elysium Vanguard Hardened Edition.
 * Fully asynchronous BLE GATT transport for OBD-II with serialized CCCD/GATT operation queues,
 * generation tokens against stale callbacks, bounded buffer memory, and semantic ELM readiness verification.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice
) : TransportInterface {

    companion object {
        private const val TAG = "BleTransport"
        private const val MAX_CONNECT_ATTEMPTS = 3
        private const val MAX_ACCUMULATOR_BYTES = 4096
    }

    // Standard UUIDs used across common OBD2 BLE adapters
    private val SERVICE_UUIDS = listOf(
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Vgate, generic
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // ELM327 BLE common
        UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"), // vLinker
        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")  // SPP over BLE
    )
    private val CHAR_WRITE_UUIDS = listOf(
        UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("bf03260c-7205-4c25-af43-93b1c299d159")
    )
    private val CHAR_NOTIFY_UUIDS = listOf(
        UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("18cda784-4bd3-4370-85bb-bfed91ec86af")
    )
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val gattMutex = Mutex()
    private val gattOperationMutex = Mutex()

    private val connectionGeneration = AtomicLong(0L)

    @Volatile
    private var connectionDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var writeDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var descriptorDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var mtuDeferred: CompletableDeferred<Int>? = null

    @Volatile
    private var servicesDeferred: CompletableDeferred<Int>? = null

    // Fragment accumulator — Thread-safe and bounded
    private val responseAccumulator = StringBuffer()
    private val responseReady = Channel<String>(64)

    // Telemetry
    val droppedResponseCount = AtomicLong(0L)
    val accumulatorOverflowCount = AtomicLong(0L)

    @Volatile
    private var connected = false

    override val isConnected: Boolean
        get() = connected && gatt != null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) {
                Log.d(TAG, "Dropping stale onConnectionStateChange from old GATT instance")
                return
            }

            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT connection status error: $status")
                connected = false
                connectionDeferred?.complete(false)
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                connected = true
                connectionDeferred?.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
                connectionDeferred?.complete(false)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status")
            mtuDeferred?.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return

            Log.d(TAG, "onServicesDiscovered status=$status")
            servicesDeferred?.complete(status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return

            Log.d(TAG, "onDescriptorWrite status=$status desc=${descriptor.uuid}")
            descriptorDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return
            writeDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return
            handleIncomingBytes(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val activeGatt = this@BleTransport.gatt
            if (activeGatt == null || activeGatt !== gatt) return
            handleIncomingBytes(value)
        }

        private fun handleIncomingBytes(value: ByteArray) {
            val chunk = String(value, Charsets.ISO_8859_1)
            if (responseAccumulator.length + chunk.length > MAX_ACCUMULATOR_BYTES) {
                Log.w(TAG, "BLE accumulator overflow protection triggered; resetting buffer")
                accumulatorOverflowCount.incrementAndGet()
                responseAccumulator.setLength(0)
            }
            responseAccumulator.append(chunk)

            if (responseAccumulator.contains('>') ||
                responseAccumulator.contains("NO DATA") ||
                responseAccumulator.contains("ERROR") ||
                responseAccumulator.contains("STOPPED")
            ) {
                val fullResponse = responseAccumulator.toString()
                responseAccumulator.setLength(0)
                val sendResult = responseReady.trySend(fullResponse)
                if (sendResult.isFailure) {
                    Log.w(TAG, "BLE response queue full, dropped response")
                    droppedResponseCount.incrementAndGet()
                }
            }
        }
    }

    override suspend fun connect() {
        var lastException: Exception? = null

        for (attempt in 1..MAX_CONNECT_ATTEMPTS) {
            try {
                Log.d(TAG, "BLE connection attempt $attempt/$MAX_CONNECT_ATTEMPTS to ${device.address}")
                val gen = connectionGeneration.incrementAndGet()
                disconnect()

                val deferred = CompletableDeferred<Boolean>()
                connectionDeferred = deferred

                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

                val linkEstablished = withTimeout(7000L) {
                    deferred.await()
                }

                if (!linkEstablished || gen != connectionGeneration.get()) {
                    disconnect()
                    continue
                }

                val currentGatt = gatt ?: throw TransportRemoteClosed("GATT instance is null")

                // Serialized GATT Operation Queue
                gattOperationMutex.withLock {
                    // 1. Request MTU
                    val mDeferred = CompletableDeferred<Int>()
                    mtuDeferred = mDeferred
                    runCatching { currentGatt.requestMtu(512) }
                    withTimeoutOrNull(2000L) { mDeferred.await() }

                    // 2. Discover Services
                    val sDeferred = CompletableDeferred<Int>()
                    servicesDeferred = sDeferred
                    currentGatt.discoverServices()
                    val sStatus = withTimeout(4000L) { sDeferred.await() }
                    if (sStatus != BluetoothGatt.GATT_SUCCESS) {
                        throw TransportRemoteClosed("Service discovery failed with status $sStatus")
                    }

                    // 3. Resolve OBD Characteristic Profile
                    var selectedWrite: BluetoothGattCharacteristic? = null
                    var selectedNotify: BluetoothGattCharacteristic? = null

                    for (service in currentGatt.services) {
                        if (SERVICE_UUIDS.contains(service.uuid)) {
                            for (char in service.characteristics) {
                                if (selectedWrite == null && CHAR_WRITE_UUIDS.contains(char.uuid)) {
                                    selectedWrite = char
                                }
                                if (selectedNotify == null && CHAR_NOTIFY_UUIDS.contains(char.uuid)) {
                                    selectedNotify = char
                                }
                            }
                        }
                    }

                    writeChar = selectedWrite ?: throw TransportRemoteClosed("No compatible OBD write characteristic found")
                    val notifyChar = selectedNotify ?: throw TransportRemoteClosed("No compatible OBD notify characteristic found")

                    // 4. Enable Notifications & Write CCCD Descriptor
                    currentGatt.setCharacteristicNotification(notifyChar, true)
                    val desc = notifyChar.getDescriptor(CCCD_UUID)
                    if (desc != null) {
                        val dDeferred = CompletableDeferred<Boolean>()
                        descriptorDeferred = dDeferred
                        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            currentGatt.writeDescriptor(desc, value)
                        } else {
                            @Suppress("DEPRECATION")
                            desc.value = value
                            @Suppress("DEPRECATION")
                            currentGatt.writeDescriptor(desc)
                        }
                        val dSuccess = withTimeout(3000L) { dDeferred.await() }
                        if (!dSuccess) {
                            throw TransportRemoteClosed("Descriptor write rejected by adapter")
                        }
                    }
                }

                if (gen == connectionGeneration.get() && writeChar != null) {
                    // Send ELM readiness probe and validate response semantically (reject '?')
                    drain()
                    val probeResult = runCatching {
                        write("\r".toByteArray(Charsets.ISO_8859_1))
                        read(128, 1500L)
                    }
                    val probeBytes = probeResult.getOrNull()
                    val probeStr = probeBytes?.let { String(it, Charsets.ISO_8859_1).trim() }

                    val isElmReady = probeResult.isSuccess && probeStr != null && (
                        probeStr.contains('>') ||
                        probeStr.contains("ELM", ignoreCase = true) ||
                        probeStr.contains("OK", ignoreCase = true) ||
                        probeStr.contains("41")
                    ) && !probeStr.contains("?")

                    if (isElmReady) {
                        Log.i(TAG, "✓ BLE GATT Link and verified ELM readiness established on attempt $attempt")
                        return
                    } else {
                        Log.w(TAG, "✗ BLE ELM prompt probe failed semantically on attempt $attempt (response='$probeStr')")
                        disconnect()
                    }
                }

                delay(500)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "BLE connect attempt $attempt failed: ${e.message}")
            }
        }

        disconnect()
        throw lastException ?: TransportRemoteClosed("Error de enlace BLE: El adaptador no completó la negociación GATT/CCCD o la sonda ELM no respondió")
    }

    override suspend fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        connected = false
        connectionDeferred?.complete(false)
        connectionDeferred = null
        writeDeferred?.complete(false)
        writeDeferred = null
        responseAccumulator.setLength(0)
    }

    override suspend fun reconnect() {
        disconnect()
        delay(500)
        connect()
    }

    override suspend fun write(data: ByteArray) {
        gattMutex.withLock {
            val char = writeChar ?: throw TransportWriteFailure("Error: Adaptador BLE no inicializado")
            val writeCompletion = CompletableDeferred<Boolean>()
            writeDeferred = writeCompletion

            val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt?.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                res == 0
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(char) ?: false
            }
            if (!initiated) {
                writeDeferred = null
                throw TransportWriteFailure("Fallo al iniciar escritura en radio BLE")
            }

            val ack = withTimeoutOrNull(2000L) {
                writeCompletion.await()
            } ?: false

            writeDeferred = null
            if (!ack) {
                throw TransportWriteFailure("Timeout o error esperando ACK de escritura BLE")
            }
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        val resp = withTimeoutOrNull(timeoutMs) {
            responseReady.receive()
        }
        return resp?.toByteArray(Charsets.ISO_8859_1)
    }

    override suspend fun drain() {
        responseAccumulator.setLength(0)
        while (!responseReady.isEmpty) {
            responseReady.tryReceive()
        }
    }
}
