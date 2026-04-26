package com.elysium369.meet.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BtClassicTransport(
    var macAddress: String,
    private val bluetoothAdapter: BluetoothAdapter
) : TransportInterface {

    // Standard SPP UUID
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
            
            repeat(3) { attempt ->
                try {
                    // Cerrar socket anterior limpiamente si existe
                    runCatching { socket?.close() }
                    socket = null
                    
                    // En Android 12+ usar método reflectivo como fallback si el normal falla
                    socket = try {
                        device.createRfcommSocketToServiceRecord(SPP_UUID)
                    } catch (e: Exception) {
                        // Fallback usando canal 1 directo (más compatible con clones chinos)
                        val m = device.javaClass.getMethod(
                            "createRfcommSocket", Int::class.javaPrimitiveType
                        )
                        m.invoke(device, 1) as BluetoothSocket
                    }
                    
                    // Cancelar discovery ANTES de conectar — crítico para estabilidad
                    try {
                        bluetoothAdapter.cancelDiscovery()
                    } catch (e: SecurityException) {
                        // Ignorar si no hay permiso
                    }
                    kotlinx.coroutines.delay(200) // dar tiempo al sistema para cancelar
                    
                    socket?.connect()
                    
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    
                    return@withContext
                    
                } catch (e: java.io.IOException) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1)) // backoff por intento
                    if (attempt == 2) throw e // If 3rd attempt fails, propagate error
                }
            }
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
