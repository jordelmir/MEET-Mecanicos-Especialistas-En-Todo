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
                    if (obdSession.state.value == ObdState.CONNECTED && !obdSession.isLivePollingPaused) {
                        try {
                            // If UDS session is active (e.g. for Active Tests), send Tester Present (3E 80)
                            // otherwise send standard OBD2 0100 heartbeat.
                            val command = if (obdSession.isUdsSessionActive.value) "3E80" else "0100"
                            obdSession.sendKeepAliveDirectly(command)
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
