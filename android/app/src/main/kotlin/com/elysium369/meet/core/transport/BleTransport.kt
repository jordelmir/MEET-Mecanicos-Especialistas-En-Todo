package com.elysium369.meet.core.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice
) : TransportInterface {

    // UUIDs que usan los clones BLE más comunes — probar todos en orden
    private val SERVICE_UUIDS = listOf(
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Vgate, genéricos
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // ELM327 BLE común
        UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"), // vLinker
        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")  // SPP sobre BLE
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

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    
    private val connectionLatch = CountDownLatch(1)
    
    // ACUMULADOR DE FRAGMENTOS — la clave para clones BLE
    private val responseAccumulator = StringBuilder()
    private val responseReady = Channel<String>(Channel.UNLIMITED)
    
    private var connected = false

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // requestMtu(512) debe ser el PRIMER comando después de onConnectionStateChange
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
                connectionLatch.countDown()
            }
        }
        
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // MTU negociado exitosamente — ahora sí iniciar servicio discovery
            gatt.discoverServices()
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    if (SERVICE_UUIDS.contains(service.uuid)) {
                        for (char in service.characteristics) {
                            if (CHAR_WRITE_UUIDS.contains(char.uuid)) {
                                writeChar = char
                            }
                            if (CHAR_NOTIFY_UUIDS.contains(char.uuid)) {
                                gatt.setCharacteristicNotification(char, true)
                                // Standard Client Characteristic Config descriptor setup
                                val desc = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                if (desc != null) {
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(desc)
                                }
                            }
                        }
                    }
                }
                connected = writeChar != null
                connectionLatch.countDown()
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
            
            // Solo emitir cuando la respuesta esté COMPLETA
            // Completa = contiene el prompt ">" de ELM327
            if (responseAccumulator.contains('>') ||
                responseAccumulator.contains("NO DATA") ||
                responseAccumulator.contains("ERROR")) {
                
                val completeResponse = responseAccumulator.toString()
                responseAccumulator.clear()
                responseReady.trySend(completeResponse)
            }
        }
    }

    override suspend fun connect() {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        val success = connectionLatch.await(10, TimeUnit.SECONDS)
        if (!success || !connected) {
            throw Exception("Failed to establish BLE GATT connection")
        }
    }

    override suspend fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        connected = false
    }

    override suspend fun write(data: ByteArray) {
        val char = writeChar ?: throw Exception("Not connected")
        char.value = data
        gatt?.writeCharacteristic(char)
    }

    override suspend fun read(maxBytes: Int): ByteArray? {
        // Here we just wait for the next full accumulated response or block
        val resp = withTimeoutOrNull(2000) {
            responseReady.receive()
        }
        return resp?.toByteArray(Charsets.ISO_8859_1)
    }

    override val isConnected: Boolean
        get() = connected
}
