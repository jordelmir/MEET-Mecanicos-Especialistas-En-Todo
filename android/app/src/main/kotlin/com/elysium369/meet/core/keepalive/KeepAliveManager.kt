package com.elysium369.meet.core.keepalive

import com.elysium369.meet.core.obd.ObdSession
import kotlinx.coroutines.*

class KeepAliveManager(
    private val session: ObdSession,
    private val scope: CoroutineScope
) {
    // El secreto: keepalive DUAL — uno para el canal, uno para el chip
    
    private val CHANNEL_KEEPALIVE_MS = 2000L  // cada 2s
    private val CHIP_WAKEUP_MS = 1800L        // ligeramente antes del canal
    
    // Comando de keepalive que funciona en el 99% de clones:
    private val keepaliveCommands = listOf("AT RV\r", "ATRV\r", "AT@1\r")
    private var keepaliveCmdIndex = 0
    
    private var lastByteReceivedAt = System.currentTimeMillis()
    private var keepaliveJob: Job? = null
    
    // LLAMAR ESTE MÉTODO cada vez que se reciben bytes del adaptador
    fun notifyBytesReceived() {
        lastByteReceivedAt = System.currentTimeMillis()
    }
    
    fun start() {
        keepaliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(500) // revisar cada 500ms con alta frecuencia
                
                val silenceMs = System.currentTimeMillis() - lastByteReceivedAt
                
                if (silenceMs >= CHIP_WAKEUP_MS) {
                    // El clon lleva 1.8s sin recibir nada — mandamos wakeup
                    // Este wakeup va FUERA de la cola normal de comandos
                    try {
                        val cmd = keepaliveCommands[keepaliveCmdIndex % keepaliveCommands.size]
                        keepaliveCmdIndex++
                        session.sendKeepAliveDirectly(cmd)
                        // No esperamos respuesta — solo importa que lleguen bytes al clon
                    } catch (e: Exception) {
                        // Si falla el keepalive → el canal está muerto
                        // session.notifyChannelDead()
                    }
                }
            }
        }
    }
    
    fun stop() { keepaliveJob?.cancel() }
}
