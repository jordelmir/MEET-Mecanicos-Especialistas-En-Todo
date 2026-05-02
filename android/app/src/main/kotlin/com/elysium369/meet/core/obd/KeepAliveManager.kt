package com.elysium369.meet.core.obd

import kotlinx.coroutines.*

/**
 * Mantiene la conexión ELM327 activa enviando pulsos AT cuando
 * no hay tráfico. Sin esto, los adaptadores BT clon cierran el socket
 * después de ~3s de inactividad.
 */
class KeepAliveManager(
    private val obdSession: ObdSession
) {
    private var keepAliveJob: Job? = null
    private var lastReceivedTime: Long = System.currentTimeMillis()

    fun notifyBytesReceived() {
        lastReceivedTime = System.currentTimeMillis()
    }

    fun start(scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(1800L)
                if (System.currentTimeMillis() - lastReceivedTime >= 1800L) {
                    if (obdSession.state.value == ObdState.CONNECTED) {
                        try {
                            // sendKeepAliveDirectly already appends \r via sendCommandDirectly
                            obdSession.sendKeepAliveDirectly("0100")
                        } catch (_: Exception) {}
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
