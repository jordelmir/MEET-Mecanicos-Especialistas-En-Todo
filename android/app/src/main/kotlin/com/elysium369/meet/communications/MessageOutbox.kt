package com.elysium369.meet.communications

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MessageOutbox — Offline-first message queue.
 *
 * Messages are enqueued locally first (LOCAL_PENDING), then uploaded when
 * connectivity is available. Failed uploads retry with exponential backoff.
 *
 * Dead-letter queue for FAILED_PERMANENT messages (audit trail).
 *
 * Laws:
 * - LOCAL TOMBSTONE → OUTBOX → SERVER ACK → LOCAL COMPACTION
 * - Never delete local immediately if sync is needed.
 * - Offline != Error.
 */
@Singleton
class MessageOutbox @Inject constructor() {

    data class OutboxEntry(
        val messageId: String,
        val conversationId: String,
        val senderPrincipalId: String,
        val ciphertextBase64: String,
        val nonceBase64: String,
        val eventType: String,
        val replyToEventId: String? = null,
        val state: MessageDeliveryState = MessageDeliveryState.LOCAL_PENDING,
        val createdAtMs: Long = System.currentTimeMillis(),
        val lastAttemptMs: Long = 0L,
        val attemptCount: Int = 0,
        val nextRetryMs: Long = 0L,
        val errorMessage: String? = null,
    )

    private val pendingQueue = ConcurrentHashMap<String, OutboxEntry>()
    private val deadLetterQueue = ConcurrentHashMap<String, OutboxEntry>()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _failedCount = MutableStateFlow(0)
    val failedCount: StateFlow<Int> = _failedCount.asStateFlow()

    /**
     * Enqueue a message for sending. Returns immediately with a messageId.
     * The message is stored locally and will be uploaded when connectivity allows.
     */
    fun enqueue(
        conversationId: String,
        senderPrincipalId: String,
        ciphertextBase64: String,
        nonceBase64: String,
        eventType: String = "TEXT",
        replyToEventId: String? = null,
    ): String {
        val messageId = UUID.randomUUID().toString()
        val entry = OutboxEntry(
            messageId = messageId,
            conversationId = conversationId,
            senderPrincipalId = senderPrincipalId,
            ciphertextBase64 = ciphertextBase64,
            nonceBase64 = nonceBase64,
            eventType = eventType,
            replyToEventId = replyToEventId,
            state = MessageDeliveryState.LOCAL_PENDING,
            createdAtMs = System.currentTimeMillis(),
        )
        pendingQueue[messageId] = entry
        _pendingCount.value = pendingQueue.size
        Log.i("MessageOutbox", "Enqueued message $messageId for conversation $conversationId")
        return messageId
    }

    /**
     * Get all pending messages for a conversation (for local display).
     */
    fun getPendingForConversation(conversationId: String): List<OutboxEntry> {
        return pendingQueue.values.filter { it.conversationId == conversationId }
            .sortedBy { it.createdAtMs }
    }

    /**
     * Mark a message as uploading (in-progress).
     */
    fun markUploading(messageId: String) {
        pendingQueue.computeIfPresent(messageId) { _, entry ->
            entry.copy(
                state = MessageDeliveryState.UPLOADING,
                lastAttemptMs = System.currentTimeMillis(),
                attemptCount = entry.attemptCount + 1,
            )
        }
    }

    /**
     * Mark a message as server-accepted (successfully uploaded).
     */
    fun markServerAccepted(messageId: String) {
        pendingQueue.computeIfPresent(messageId) { _, entry ->
            entry.copy(state = MessageDeliveryState.SERVER_ACCEPTED)
        }
        // Remove from pending after brief window for state propagation
        CoroutineScope(Dispatchers.IO).launch {
            delay(5_000)
            pendingQueue.remove(messageId)
            _pendingCount.value = pendingQueue.size
        }
    }

    /**
     * Mark a message as delivered (recipient device confirmed).
     */
    fun markDelivered(messageId: String) {
        pendingQueue.computeIfPresent(messageId) { _, entry ->
            entry.copy(state = MessageDeliveryState.DELIVERED)
        }
    }

    /**
     * Mark a message as read by recipient.
     */
    fun markRead(messageId: String) {
        pendingQueue.computeIfPresent(messageId) { _, entry ->
            entry.copy(state = MessageDeliveryState.READ)
        }
    }

    /**
     * Mark a message upload as failed (retryable).
     * Applies exponential backoff: 5s, 15s, 45s, 135s, 405s...
     */
    fun markFailedRetryable(messageId: String, error: String) {
        pendingQueue.computeIfPresent(messageId) { _, entry ->
            val backoffMs = calculateBackoff(entry.attemptCount)
            entry.copy(
                state = MessageDeliveryState.FAILED_RETRYABLE,
                errorMessage = error,
                lastAttemptMs = System.currentTimeMillis(),
                attemptCount = entry.attemptCount + 1,
                nextRetryMs = System.currentTimeMillis() + backoffMs,
            )
        }
        _pendingCount.value = pendingQueue.size
    }

    /**
     * Mark a message as permanently failed (dead-letter).
     */
    fun markFailedPermanent(messageId: String, error: String) {
        val entry = pendingQueue.remove(messageId)?.copy(
            state = MessageDeliveryState.FAILED_PERMANENT,
            errorMessage = error,
        ) ?: return
        deadLetterQueue[messageId] = entry
        _pendingCount.value = pendingQueue.size
        _failedCount.value = deadLetterQueue.size
        Log.w("MessageOutbox", "Message $messageId moved to dead-letter: $error")
    }

    /**
     * Get messages ready for retry (FAILED_RETRYABLE with backoff elapsed).
     */
    fun getRetryable(): List<OutboxEntry> {
        val now = System.currentTimeMillis()
        return pendingQueue.values.filter {
            it.state == MessageDeliveryState.FAILED_RETRYABLE && now >= it.nextRetryMs
        }.sortedBy { it.createdAtMs }
    }

    /**
     * Get messages pending upload (LOCAL_PENDING or FAILED_RETRYABLE ready for retry).
     */
    fun getReadyForUpload(): List<OutboxEntry> {
        val now = System.currentTimeMillis()
        return pendingQueue.values.filter {
            it.state == MessageDeliveryState.LOCAL_PENDING ||
                (it.state == MessageDeliveryState.FAILED_RETRYABLE && now >= it.nextRetryMs)
        }.sortedBy { it.createdAtMs }
    }

    /**
     * Get dead-letter entries for audit/debug.
     */
    fun getDeadLetters(): List<OutboxEntry> = deadLetterQueue.values.toList()

    /**
     * Clear a specific dead-letter entry (admin action only).
     */
    fun clearDeadLetter(messageId: String) {
        deadLetterQueue.remove(messageId)
        _failedCount.value = deadLetterQueue.size
    }

    /**
     * Get the current state of a message.
     */
    fun getMessageState(messageId: String): MessageDeliveryState? {
        return pendingQueue[messageId]?.state ?: deadLetterQueue[messageId]?.state
    }

    /**
     * Exponential backoff with jitter.
     * Base: 5 seconds, multiplier: 3x, max: ~7 minutes.
     */
    private fun calculateBackoff(attemptCount: Int): Long {
        val baseMs = 5_000L
        val multiplier = 3L
        val maxMs = 405_000L
        val backoff = (baseMs * Math.pow(multiplier.toDouble(), attemptCount.toDouble())).toLong()
        val jitter = (Math.random() * 1000).toLong()
        return (backoff + jitter).coerceAtMost(maxMs)
    }
}
