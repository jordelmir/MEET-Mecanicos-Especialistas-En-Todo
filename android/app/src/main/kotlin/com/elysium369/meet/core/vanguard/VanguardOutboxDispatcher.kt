package com.elysium369.meet.core.vanguard

import com.elysium369.meet.data.local.dao.VanguardCommerceDao
import com.elysium369.meet.data.local.entities.VanguardEventEntity
import com.elysium369.meet.data.local.entities.VanguardOutboxEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface VanguardOutboxDispatcher {
    suspend fun enqueue(event: VanguardOutboxEvent)
    suspend fun drain(): Int
    suspend fun pendingCount(): Int
}

data class VanguardOutboxEvent(
    val id: String,
    val topic: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val attempts: Int = 0,
)

/**
 * Durable Room outbox with idempotent Supabase delivery.
 *
 * A delivery is marked DELIVERED only after the remote upsert returns. Unknown
 * topics, malformed payloads and authentication/network failures remain queued
 * or become dead letters; none are converted into a false success.
 */
@Singleton
class SupabaseVanguardOutboxDispatcher @Inject constructor(
    private val dao: VanguardCommerceDao,
    private val supabase: SupabaseClient,
) : VanguardOutboxDispatcher {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    override suspend fun enqueue(event: VanguardOutboxEvent) {
        require(event.id.isNotBlank()) { "Vanguard outbox event id is required" }
        require(event.topic in SUPPORTED_TOPICS) { "Unsupported Vanguard outbox topic: ${event.topic}" }
        val payload = parsePayload(event.payloadJson)
        val now = event.createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val stableKey = "${event.topic}:${event.id}"
        dao.recordCommerceEvent(
            event = VanguardEventEntity(
                eventId = event.id,
                aggregateType = event.topic.substringBefore('.').uppercase(),
                aggregateId = payload.stringValue("reportId") ?: event.id,
                eventType = event.topic.substringAfter('.', "upsert").uppercase(),
                actorId = payload.stringValue("userId"),
                actorRole = null,
                source = "LOCAL_ROOM",
                idempotencyKey = stableKey,
                payloadJson = event.payloadJson,
                occurredAt = now,
            ),
            outbox = VanguardOutboxEntity(
                outboxId = UUID.nameUUIDFromBytes(stableKey.toByteArray()).toString(),
                eventId = event.id,
                destination = "SUPABASE_EVENTS",
                operation = event.topic,
                payloadJson = event.payloadJson,
                status = "PENDING",
                attemptCount = event.attempts.coerceAtLeast(0),
                nextAttemptAt = now,
                createdAt = now,
                updatedAt = now,
                idempotencyKey = stableKey,
            )
        )
    }

    override suspend fun drain(): Int {
        val now = System.currentTimeMillis()
        dao.recoverStaleOutbox(now - STALE_IN_FLIGHT_MS, now)
        val ready = (
            dao.getOutboxByStatus("PENDING", now, BATCH_SIZE) +
                dao.getOutboxByStatus("FAILED", now, BATCH_SIZE)
            ).distinctBy { it.outboxId }.take(BATCH_SIZE)
        var delivered = 0
        for (message in ready) {
            if (dao.acquireOutbox(message.outboxId, System.currentTimeMillis()) != 1) continue
            val result = runCatching {
                val table = SUPPORTED_TOPICS[message.operation]
                    ?: error("Unsupported Vanguard outbox operation: ${message.operation}")
                supabase.postgrest[table].upsert(parsePayload(message.payloadJson))
            }
            result.onSuccess {
                check(dao.markOutboxDelivered(message.outboxId, System.currentTimeMillis()) == 1) {
                    "Lost Vanguard outbox lease for ${message.outboxId}"
                }
                dao.markEventsSynced(listOf(message.eventId))
                delivered++
            }.onFailure { error ->
                val attempts = message.attemptCount + 1
                val terminal = attempts >= MAX_ATTEMPTS || error is IllegalArgumentException
                val retryAt = if (terminal) Long.MAX_VALUE else System.currentTimeMillis() + retryDelayMs(attempts)
                dao.markOutboxFailure(
                    outboxId = message.outboxId,
                    status = if (terminal) "DEAD_LETTER" else "FAILED",
                    error = error.safeMessage(),
                    nextAttemptAt = retryAt,
                    now = System.currentTimeMillis(),
                )
            }
        }
        return delivered
    }

    override suspend fun pendingCount(): Int = dao.pendingOutboxCount()

    private fun parsePayload(raw: String): JsonObject =
        (json.parseToJsonElement(raw) as? JsonObject)
            ?: throw IllegalArgumentException("Vanguard outbox payload must be a JSON object")

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.toString()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }

    private fun retryDelayMs(attempt: Int): Long =
        (BASE_RETRY_MS * (1L shl (attempt - 1).coerceIn(0, 6))).coerceAtMost(MAX_RETRY_MS)

    private fun Throwable.safeMessage(): String =
        (message ?: javaClass.simpleName).take(MAX_ERROR_LENGTH)

    private companion object {
        val SUPPORTED_TOPICS = mapOf("certified_reports.upsert" to "certified_reports")
        const val BATCH_SIZE = 25
        const val MAX_ATTEMPTS = 8
        const val STALE_IN_FLIGHT_MS = 15 * 60_000L
        const val BASE_RETRY_MS = 30_000L
        const val MAX_RETRY_MS = 30 * 60_000L
        const val MAX_ERROR_LENGTH = 512
    }
}

/** Deterministic in-memory dispatcher for unit tests and explicit offline tools. */
class LocalVanguardOutboxDispatcher : VanguardOutboxDispatcher {
    private val mutex = Mutex()
    private val buffer = ArrayDeque<VanguardOutboxEvent>()

    override suspend fun enqueue(event: VanguardOutboxEvent) = mutex.withLock {
        if (buffer.none { it.id == event.id && it.topic == event.topic }) buffer.addLast(event)
    }

    override suspend fun drain(): Int = mutex.withLock {
        val count = buffer.size
        buffer.clear()
        count
    }

    override suspend fun pendingCount(): Int = mutex.withLock { buffer.size }
}
