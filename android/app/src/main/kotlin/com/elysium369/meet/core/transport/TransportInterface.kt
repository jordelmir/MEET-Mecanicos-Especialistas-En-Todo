package com.elysium369.meet.core.transport

interface TransportInterface {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun reconnect()
    suspend fun write(data: ByteArray)
    suspend fun read(maxBytes: Int): ByteArray?
    val isConnected: Boolean
}
