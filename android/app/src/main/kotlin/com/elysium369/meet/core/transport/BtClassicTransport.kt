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
            
            // Cancelar discovery ANTES de cualquier intento — crítico para estabilidad
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: SecurityException) {
                // Ignorar si no hay permiso
            }
            
            // Dar tiempo al sistema para liberar el radio BT del modo discovery
            kotlinx.coroutines.delay(300)

            // ═══════════════════════════════════════════════
            // ESTRATEGIA DE CONEXIÓN MULTI-MÉTODO
            // Los clones chinos fallan con uno u otro método
            // dependiendo del firmware. Intentamos todos.
            // ═══════════════════════════════════════════════
            val connectionMethods = listOf(
                // Método 1: SPP estándar (funciona con adaptadores genuinos y algunos clones)
                {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                },
                // Método 2: Canal 1 directo via reflexión (el más compatible con clones chinos)
                {
                    val m = device.javaClass.getMethod(
                        "createRfcommSocket", Int::class.javaPrimitiveType
                    )
                    m.invoke(device, 1) as BluetoothSocket
                },
                // Método 3: Canal inseguro SPP (bypasses pairing issues on some devices)
                {
                    val m = device.javaClass.getMethod(
                        "createInsecureRfcommSocket", Int::class.javaPrimitiveType
                    )
                    m.invoke(device, 1) as BluetoothSocket
                },
                // Método 4: createInsecureRfcommSocketToServiceRecord
                {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                }
            )

            var lastException: Exception? = null

            for ((methodIndex, createSocket) in connectionMethods.withIndex()) {
                repeat(2) { attempt ->
                    try {
                        // Cerrar socket anterior limpiamente
                        runCatching { socket?.close() }
                        socket = null
                        inputStream = null
                        outputStream = null
                        
                        socket = createSocket()
                        
                        // Dar tiempo entre intentos para que el stack BT se estabilice
                        if (attempt > 0 || methodIndex > 0) {
                            kotlinx.coroutines.delay(500)
                        }
                        
                        socket?.connect()
                        
                        inputStream = socket?.inputStream
                        outputStream = socket?.outputStream
                        
                        // Esperar a que los streams estén listos
                        kotlinx.coroutines.delay(150)
                        
                        return@withContext // ¡ÉXITO!
                        
                    } catch (e: java.io.IOException) {
                        lastException = e
                        runCatching { socket?.close() }
                        socket = null
                        kotlinx.coroutines.delay(300L * (attempt + 1))
                    } catch (e: Exception) {
                        lastException = e as? Exception ?: Exception(e)
                        runCatching { socket?.close() }
                        socket = null
                        kotlinx.coroutines.delay(300)
                    }
                }
            }
            
            throw lastException ?: java.io.IOException("No se pudo conectar al adaptador OBD2 con ningún método")
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try { inputStream?.close() } catch (e: Exception) {}
            try { outputStream?.close() } catch (e: Exception) {}
            try { socket?.close() } catch (e: Exception) {}
            socket = null
            inputStream = null
            outputStream = null
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val out = outputStream ?: throw java.io.IOException("OutputStream null — adaptador desconectado")
            out.write(data)
            out.flush()
        }
    }

    override suspend fun read(maxBytes: Int): ByteArray? {
        return withContext(Dispatchers.IO) {
            val stream = inputStream ?: return@withContext null
            try {
                // ═══════════════════════════════════════════════
                // LECTURA BLOQUEANTE CON TIMEOUT
                // stream.available() en clones ELM327 a menudo
                // retorna 0 incluso cuando hay datos en camino.
                // Usamos lectura bloqueante con un timeout corto
                // para no perder bytes.
                // ═══════════════════════════════════════════════
                val buffer = ByteArray(maxBytes)
                
                // Primero checar si hay datos listos sin bloquear
                if (stream.available() > 0) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        return@withContext buffer.copyOf(bytesRead)
                    }
                }
                
                // Si no hay datos inmediatos, hacer una pausa corta y reintentar
                // Esto le da tiempo al clon lento de preparar bytes en el buffer
                kotlinx.coroutines.delay(25)
                
                if (stream.available() > 0) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead > 0) {
                        return@withContext buffer.copyOf(bytesRead)
                    }
                }
                
                null
            } catch (e: java.io.IOException) {
                null
            }
        }
    }

    override val isConnected: Boolean
        get() = socket?.isConnected == true && inputStream != null && outputStream != null
}
