package com.elysium369.meet.core.keepalive

import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import kotlinx.coroutines.*

/**
 * ReconnectPolicy — Maneja reconexión automática cuando se pierde
 * la conexión con el adaptador OBD2.
 * 
 * Usa Exponential Backoff con tope máximo para evitar stack overflow
 * (la versión anterior era recursiva sin límite).
 */
class ReconnectPolicy(
    private val session: ObdSession,
    private val onNotifyUser: (String) -> Unit
) {
    private var job: Job? = null
    private var failCount = 0
    private val maxRetries = 5
    // Backoff delays en ms: 2s, 4s, 8s, 16s, 30s
    private val backoffDelays = longArrayOf(2000, 4000, 8000, 16000, 30000)

    fun onDisconnectDetected() {
        if (job?.isActive == true) return
        
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // ═══════════════════════════════════════════════
            // BUCLE ITERATIVO (no recursivo) con backoff
            // La versión anterior usaba recursión sin límite
            // lo cual causaba StackOverflow en desconexiones
            // prolongadas (ej: carro apagado por 10+ min)
            // ═══════════════════════════════════════════════
            while (failCount < maxRetries && isActive) {
                try {
                    // 1. Primer intento rápido: wake-up ping
                    if (failCount == 0) {
                        val response = session.sendRawCommand("AT")
                        if (response.contains("OK") || response.contains("?") || response.contains("ELM")) {
                            failCount = 0
                            return@launch
                        }
                    }
                } catch (_: Exception) {
                    // Adaptador no respondió — continuar con reconexión completa
                }

                failCount++
                
                // 2. Backoff delay
                val delayMs = backoffDelays[minOf(failCount - 1, backoffDelays.size - 1)]
                delay(delayMs)
                
                // 3. Reconexión completa
                try {
                    session.disconnect()
                    delay(500) // Dar tiempo al stack BT para limpiar el socket
                    session.connect()
                    
                    if (session.state.value == ObdState.CONNECTED) {
                        failCount = 0
                        return@launch // ¡Reconexión exitosa!
                    }
                } catch (_: Exception) {
                    // Continuar al siguiente intento
                }
            }

            // Demasiados fallos — notificar al usuario
            failCount = 0
            withContext(Dispatchers.Main) {
                onNotifyUser("Adaptador desconectado — Toca para reconectar")
            }
        }
    }

    fun reset() {
        failCount = 0
        job?.cancel()
        job = null
    }
}
