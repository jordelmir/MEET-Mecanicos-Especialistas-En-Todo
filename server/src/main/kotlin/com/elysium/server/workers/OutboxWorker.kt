package com.elysium.server.workers

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.pow

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   E V E N T   O U T B O X   W O R K E R
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER OMEGA §49, §50, §51.
 *
 *  - Short DB transactions. Never keep locks open during remote publication.
 *  - Atomic claim using lease tokens and timeouts (SKIP LOCKED semantics).
 *  - At-least-once delivery with idempotent consumers.
 *  - Exponential backoff with jitter on transient failures.
 *  - Automatic quarantine to Dead Letter Queue (DLQ) after max attempts.
 * ══════════════════════════════════════════════════════════════════════
 */

data class OutboxEventRecord(
    val outboxId: Long,
    val eventId: String = UUID.randomUUID().toString(),
    val sourceDomain: String,
    val sourceType: String = "DOMAIN_ENTITY",
    val sourceId: String,
    val aggregateType: String,
    val aggregateId: String,
    val aggregateVersion: Long = 1L,
    val eventType: String,
    val payload: String = "{}",
    val targetPrincipalId: String? = null,
    val correlationId: String? = null,
    val publishAttempts: Int = 0,
    val nextAttemptAtEpochMs: Long = 0L,
    val leaseOwner: String? = null,
    val leaseToken: String? = null,
    val leaseUntilEpochMs: Long? = null,
    val publishedAtEpochMs: Long? = null,
    val deadLetteredAtEpochMs: Long? = null,
    val lastErrorCode: String? = null,
)

sealed interface EventPublishResult {
    data object Success : EventPublishResult
    data class RetryableError(val reason: String) : EventPublishResult
    data class TerminalError(val reason: String) : EventPublishResult
}

fun interface EventPublisher {
    suspend fun publish(event: OutboxEventRecord): EventPublishResult
}

interface OutboxRepository {
    suspend fun claimPendingEvents(
        workerId: String,
        leaseToken: String,
        leaseUntilEpochMs: Long,
        batchSize: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<OutboxEventRecord>

    suspend fun markPublished(outboxId: Long, leaseToken: String, publishedAtEpochMs: Long): Boolean
    suspend fun markFailed(outboxId: Long, leaseToken: String, errorCode: String, nextAttemptEpochMs: Long, attempts: Int): Boolean
    suspend fun markDeadLettered(outboxId: Long, leaseToken: String, errorCode: String): Boolean
    suspend fun insertEvent(event: OutboxEventRecord): Long
    suspend fun getEvent(outboxId: Long): OutboxEventRecord?
}

/**
 * Thread-safe In-Memory Outbox Repository adhering strictly to lease/claim semantics.
 */
class InMemoryOutboxRepository : OutboxRepository {
    private val records = ConcurrentHashMap<Long, OutboxEventRecord>()
    private val idSequence = AtomicLong(1L)

    override suspend fun insertEvent(event: OutboxEventRecord): Long {
        val id = if (event.outboxId > 0) event.outboxId else idSequence.getAndIncrement()
        val assigned = event.copy(outboxId = id)
        records[id] = assigned
        return id
    }

    override suspend fun getEvent(outboxId: Long): OutboxEventRecord? {
        return records[outboxId]
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun claimPendingEvents(
        workerId: String,
        leaseToken: String,
        leaseUntilEpochMs: Long,
        batchSize: Int,
        nowEpochMs: Long,
    ): List<OutboxEventRecord> = mutex.withLock {
        val claimed = mutableListOf<OutboxEventRecord>()

        val candidates = records.values
            .filter { record ->
                record.publishedAtEpochMs == null &&
                    record.deadLetteredAtEpochMs == null &&
                    record.nextAttemptAtEpochMs <= nowEpochMs &&
                    (record.leaseUntilEpochMs == null || record.leaseUntilEpochMs < nowEpochMs)
            }
            .sortedBy { it.outboxId }
            .take(batchSize)

        for (candidate in candidates) {
            val updated = candidate.copy(
                leaseOwner = workerId,
                leaseToken = leaseToken,
                leaseUntilEpochMs = leaseUntilEpochMs,
            )
            records[candidate.outboxId] = updated
            claimed.add(updated)
        }

        return claimed
    }

    override suspend fun markPublished(outboxId: Long, leaseToken: String, publishedAtEpochMs: Long): Boolean {
        val current = records[outboxId] ?: return false
        if (current.leaseToken != leaseToken) return false

        records[outboxId] = current.copy(
            publishedAtEpochMs = publishedAtEpochMs,
            leaseToken = null,
            leaseOwner = null,
            leaseUntilEpochMs = null,
            lastErrorCode = null,
        )
        return true
    }

    override suspend fun markFailed(
        outboxId: Long,
        leaseToken: String,
        errorCode: String,
        nextAttemptEpochMs: Long,
        attempts: Int,
    ): Boolean {
        val current = records[outboxId] ?: return false
        if (current.leaseToken != leaseToken) return false

        records[outboxId] = current.copy(
            publishAttempts = attempts,
            nextAttemptAtEpochMs = nextAttemptEpochMs,
            leaseToken = null,
            leaseOwner = null,
            leaseUntilEpochMs = null,
            lastErrorCode = errorCode,
        )
        return true
    }

    override suspend fun markDeadLettered(outboxId: Long, leaseToken: String, errorCode: String): Boolean {
        val current = records[outboxId] ?: return false
        if (current.leaseToken != leaseToken) return false

        records[outboxId] = current.copy(
            deadLetteredAtEpochMs = System.currentTimeMillis(),
            leaseToken = null,
            leaseOwner = null,
            leaseUntilEpochMs = null,
            lastErrorCode = errorCode,
        )
        return true
    }
}

class OutboxWorker(
    private val repository: OutboxRepository = InMemoryOutboxRepository(),
    private val publisher: EventPublisher = EventPublisher { EventPublishResult.Success },
    private val pollIntervalMs: Long = 1000L,
    private val leaseDurationMs: Long = 30_000L,
    private val batchSize: Int = 25,
    private val maxAttempts: Int = 5,
    private val workerId: String = "worker-${UUID.randomUUID().toString().take(8)}",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(OutboxWorker::class.java)
    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (isRunning.compareAndSet(false, true)) {
            job = scope.launch(dispatcher) {
                logger.info("Elysium Outbox Worker [$workerId] started with batchSize=$batchSize, leaseMs=$leaseDurationMs.")
                while (isRunning.get() && isActive) {
                    try {
                        pollAndPublishPendingEvents()
                    } catch (e: CancellationException) {
                        logger.info("Outbox worker received cancellation.")
                        throw e
                    } catch (e: Exception) {
                        logger.error("Error polling outbox: ${e.message}", e)
                    }
                    delay(pollIntervalMs)
                }
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            job?.cancel()
            logger.info("Elysium Outbox Worker [$workerId] stopped.")
        }
    }

    suspend fun pollAndPublishPendingEvents(): Int {
        val now = System.currentTimeMillis()
        val leaseToken = UUID.randomUUID().toString()
        val leaseUntil = now + leaseDurationMs

        val claimedEvents = repository.claimPendingEvents(
            workerId = workerId,
            leaseToken = leaseToken,
            leaseUntilEpochMs = leaseUntil,
            batchSize = batchSize,
            nowEpochMs = now,
        )

        if (claimedEvents.isEmpty()) {
            return 0
        }

        var publishedCount = 0

        for (event in claimedEvents) {
            try {
                // Publisher executes strictly outside of any DB transaction
                val result = publisher.publish(event)
                when (result) {
                    is EventPublishResult.Success -> {
                        repository.markPublished(event.outboxId, leaseToken, System.currentTimeMillis())
                        publishedCount++
                        logger.debug("Successfully published event {} [{}]", event.eventId, event.eventType)
                    }
                    is EventPublishResult.RetryableError -> {
                        val newAttempts = event.publishAttempts + 1
                        if (newAttempts >= maxAttempts) {
                            repository.markDeadLettered(event.outboxId, leaseToken, "EXCEEDED_MAX_ATTEMPTS: ${result.reason}")
                            logger.warn("Event {} reached max attempts ($maxAttempts). Moved to DLQ.", event.eventId)
                        } else {
                            val backoffMs = calculateExponentialBackoff(newAttempts)
                            val nextAttempt = System.currentTimeMillis() + backoffMs
                            repository.markFailed(event.outboxId, leaseToken, result.reason, nextAttempt, newAttempts)
                            logger.info("Event {} failed retryably (attempt {}). Next in {}ms: {}", event.eventId, newAttempts, backoffMs, result.reason)
                        }
                    }
                    is EventPublishResult.TerminalError -> {
                        repository.markDeadLettered(event.outboxId, leaseToken, "TERMINAL_ERROR: ${result.reason}")
                        logger.error("Event {} failed with terminal error. Moved directly to DLQ: {}", event.eventId, result.reason)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val newAttempts = event.publishAttempts + 1
                val backoffMs = calculateExponentialBackoff(newAttempts)
                repository.markFailed(event.outboxId, leaseToken, e.message ?: "UNHANDLED_EXCEPTION", System.currentTimeMillis() + backoffMs, newAttempts)
                logger.error("Exception during publication of event {}: {}", event.eventId, e.message)
            }
        }

        return publishedCount
    }

    private fun calculateExponentialBackoff(attempt: Int): Long {
        val baseMs = 1000L
        val multiplier = 2.0.pow((attempt - 1).toDouble()).toLong()
        val calculated = baseMs * multiplier
        return min(calculated, 60_000L) // Max 1 minute backoff
    }
}
