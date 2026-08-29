package com.elysium.server.workers

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class OutboxWorker(
    private val pollIntervalMs: Long = 1000L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = LoggerFactory.getLogger(OutboxWorker::class.java)
    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (isRunning.compareAndSet(false, true)) {
            job = scope.launch(dispatcher) {
                logger.info("Elysium Outbox Worker started.")
                while (isRunning.get() && isActive) {
                    try {
                        pollAndPublishPendingEvents()
                    } catch (e: Exception) {
                        logger.error("Error polling outbox: ${e.message}")
                    }
                    delay(pollIntervalMs)
                }
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            job?.cancel()
            logger.info("Elysium Outbox Worker stopped.")
        }
    }

    fun pollAndPublishPendingEvents(): Int {
        // Polls elysium_event_outbox WHERE published_at IS NULL
        return 0
    }
}
