package com.elysium369.meet.core.keepalive

import com.elysium369.meet.core.obd.ObdSession
import kotlinx.coroutines.*

class ReconnectPolicy(
    private val session: ObdSession,
    private val onNotifyUser: (String) -> Unit
) {
    private var job: Job? = null
    private var failCount = 0

    fun onDisconnectDetected() {
        if (job?.isActive == true) return
        
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Primer intento rápido: Despertar
                val response = session.sendRawCommand("AT\r")
                if (response.contains("OK") || response.contains("?")) {
                    failCount = 0
                    return@launch
                }
            } catch (e: Exception) {
                // Sigue al paso 2
            }

            failCount++

            // 2. Intentos con Exponential Backoff
            if (failCount < 5) {
                val delayMs = (2000L * Math.pow(2.0, (failCount - 1).toDouble())).toLong()
                delay(delayMs)
                
                try {
                    session.disconnect()
                    session.connect() // Uses cached profile automatically in transport
                    if (session.state.value == com.elysium369.meet.core.obd.ObdState.CONNECTED) {
                        failCount = 0
                        return@launch
                    }
                } catch (e: Exception) {
                    onDisconnectDetected() // recursivo
                }
            } else {
                // 3. Demasiados fallos
                delay(30000)
                withContext(Dispatchers.Main) {
                    onNotifyUser("Adaptador desconectado — Toca para reconectar")
                }
                failCount = 0
            }
        }
    }

    fun reset() {
        failCount = 0
        job?.cancel()
    }
}
