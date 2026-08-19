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

@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice
) : TransportInterface {

    private val TAG = "EV_BLE"

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

    @Volatile
    private var connectionDeferred: CompletableDeferred<Boolean>? = null

    // Fragment accumulator — Thread-safe
    private val responseAccumulator = StringBuffer()
    private val responseReady = Channel<String>(Channel.UNLIMITED)

    @Volatile
    private var connected = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT connection status error: $status")
                connected = false
                connectionDeferred?.complete(false)
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                runCatching { gatt.requestMtu(512) }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
                connectionDeferred?.complete(false)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu status=$status")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                var foundWrite = false
                var cccdPending = false

                for (service in gatt.services) {
                    if (SERVICE_UUIDS.contains(service.uuid)) {
                        for (char in service.characteristics) {
                            if (CHAR_WRITE_UUIDS.contains(char.uuid)) {
                                writeChar = char
                                foundWrite = true
                            }
                            if (CHAR_NOTIFY_UUIDS.contains(char.uuid)) {
                                gatt.setCharacteristicNotification(char, true)
                                val desc = char.getDescriptor(CCCD_UUID)
                                if (desc != null) {
                                    cccdPending = true
                                    val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        gatt.writeDescriptor(desc, value)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        desc.value = value
                                        @Suppress("DEPRECATION")
                                        gatt.writeDescriptor(desc)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!cccdPending && foundWrite) {
                    connected = true
                    connectionDeferred?.complete(true)
                }
            } else {
                connectionDeferred?.complete(false)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=$status desc=${descriptor.uuid}")
            if (descriptor.uuid == CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                connected = (writeChar != null)
                connectionDeferred?.complete(connected)
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                connectionDeferred?.complete(false)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleIncomingBytes(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        private fun handleIncomingBytes(value: ByteArray) {
            val chunk = String(value, Charsets.ISO_8859_1)
            responseAccumulator.append(chunk)

            if (responseAccumulator.contains('>') ||
                responseAccumulator.contains("NO DATA") ||
                responseAccumulator.contains("ERROR") ||
                responseAccumulator.contains("STOPPED")) {

                val completeResponse = responseAccumulator.toString()
                responseAccumulator.setLength(0)
                responseReady.trySend(completeResponse)
            }
        }
    }

    override suspend fun connect() {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                gatt?.disconnect()
                gatt?.close()

                val deferred = CompletableDeferred<Boolean>()
                connectionDeferred = deferred

                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

                val success = withTimeout(7000L) {
                    deferred.await()
                }

                if (success && connected) {
                    // Send ELM readiness probe before declaring link operational
                    drain()
                    runCatching {
                        write("\r".toByteArray(Charsets.ISO_8859_1))
                        read(128, 1500L)
                    }
                    Log.i(TAG, "✓ BLE GATT Link and ELM readiness established on attempt $attempt")
                    return
                }

                delay(500)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "BLE connect attempt $attempt failed: ${e.message}")
            }
        }

        disconnect()
        throw lastException ?: TransportRemoteClosed("Error de enlace BLE: El adaptador no completó la negociación GATT/CCCD")
    }

    override suspend fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        connected = false
        connectionDeferred?.complete(false)
        connectionDeferred = null
    }

    override suspend fun reconnect() {
        disconnect()
        delay(500)
        connect()
    }

    override suspend fun write(data: ByteArray) {
        gattMutex.withLock {
            val char = writeChar ?: throw TransportWriteFailure("Error: Adaptador BLE no inicializado")
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt?.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                res == 0
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                gatt?.writeCharacteristic(char) ?: false
            }
            if (!success) {
                throw TransportWriteFailure("Fallo al escribir en el radio BLE")
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

    override val isConnected: Boolean
        get() = connected
}
