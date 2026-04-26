package com.elysium369.meet.core.obd

import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepAliveManager @Inject constructor(
    private val obdSession: ObdSession
) {
    private var keepAliveJob: Job? = null
    private var lastReceivedTime: Long = System.currentTimeMillis()

    fun notifyBytesReceived() {
        lastReceivedTime = System.currentTimeMillis()
    }

    fun start() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(1800L) // 1.8s check interval
                if (System.currentTimeMillis() - lastReceivedTime >= 1800L) {
                    if (obdSession.state.value == ObdState.CONNECTED) {
                        // Enviar comando para mantener viva la conexión
                        try {
                            obdSession.sendKeepAliveDirectly("0100\r")
                        } catch (e: Exception) {
                            // Ignorar errores del keepalive
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }
}
