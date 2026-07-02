package com.elysium369.meet.core.vanguard

/**
 * STUB FILE — Dispatcher para outbox de eventos Vanguard → Supabase.
 * Real impl persiste en local Room y sincroniza a Supabase.
 * Este stub satisface el compile sin bloquear compilación del resto del módulo.
 */

interface VanguardOutboxDispatcher {
    fun enqueue(event: VanguardOutboxEvent)
    suspend fun drain(): Int
    suspend fun pendingCount(): Int
}

data class VanguardOutboxEvent(
    val id: String,
    val topic: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val attempts: Int = 0
)

/**
 * Implementación Supabase (stub) del dispatcher.
 */
class SupabaseVanguardOutboxDispatcher : VanguardOutboxDispatcher {
    override fun enqueue(event: VanguardOutboxEvent) {}
    override suspend fun drain(): Int = 0
    override suspend fun pendingCount(): Int = 0
}

/**
 * Implementación local (no remote) para entornos offline o testing.
 */
class LocalVanguardOutboxDispatcher : VanguardOutboxDispatcher {
    private val buffer = ArrayDeque<VanguardOutboxEvent>()

    override fun enqueue(event: VanguardOutboxEvent) {
        buffer.addLast(event)
    }

    override suspend fun drain(): Int {
        val n = buffer.size
        buffer.clear()
        return n
    }

    override suspend fun pendingCount(): Int = buffer.size
}