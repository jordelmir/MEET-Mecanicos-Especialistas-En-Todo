package com.elysium369.meet.core.transport

import com.elysium369.meet.core.obd.TransportLinkEvent
import com.elysium369.meet.core.obd.TransportLinkState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TransportInterface {
    suspend fun connect()
    /** Immediately abort an in-flight blocking connect without waiting on transport locks. */
    fun abortConnect()
    suspend fun disconnect()
    suspend fun write(data: ByteArray)
    suspend fun read(maxBytes: Int, timeoutMs: Long = 600L): ByteArray?
    suspend fun drain()
    val isConnected: Boolean
    val linkState: StateFlow<TransportLinkState>
    val linkEvents: SharedFlow<TransportLinkEvent>
}
