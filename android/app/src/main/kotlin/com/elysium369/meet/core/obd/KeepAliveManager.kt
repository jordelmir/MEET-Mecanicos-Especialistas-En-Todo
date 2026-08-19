package com.elysium369.meet.core.obd

import kotlinx.coroutines.*

/**
 * Mantiene la conexión ELM327 activa enviando pulsos calibrados por política de protocolo
 * cuando no hay tráfico.
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
                delay(1500L)
                val idleMs = System.currentTimeMillis() - lastReceivedTime
                if (obdSession.state.value == ObdState.CONNECTED && !obdSession.isLivePollingPaused) {
                    val protocol = ObdProtocol.fromString(obdSession.detectedProtocol)
                    val isUds = obdSession.isUdsSessionActive.value
                    if (ProtocolKeepAlivePolicy.shouldSendKeepAlive(protocol, isUds, idleMs)) {
                        try {
                            val command = if (isUds) "3E80" else "0100"
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
