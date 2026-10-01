package com.elysium.nexus.fabric.automation

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [AutomationStore].
 *
 * The dedup window is [DEDUP_WINDOW_MS]
 * (5 minutes). After that window, the
 * idempotency key is eligible for cleanup.
 *
 * This store is **not** persisted. The
 * production store is a Room-backed
 * implementation that survives process
 * death.
 */
class DefaultAutomationStore : AutomationStore {

    companion object {
        /** The dedup window in milliseconds. */
        const val DEDUP_WINDOW_MS: Long = 5 * 60 * 1000L

        /** Maximum keys to prevent unbounded growth. */
        const val MAX_KEYS: Int = 1000
    }

    /** In-flight keys with their timestamp. */
    private val inFlight = ConcurrentHashMap<String, Long>()

    override fun isInFlight(key: IdempotencyKey): Boolean {
        val ts = inFlight[key.value] ?: return false
        // Check if the key has expired.
        if (System.currentTimeMillis() - ts > DEDUP_WINDOW_MS) {
            inFlight.remove(key.value)
            return false
        }
        return true
    }

    override fun markInFlight(key: IdempotencyKey) {
        // Evict oldest if at capacity.
        if (inFlight.size >= MAX_KEYS) {
            val oldest = inFlight.entries
                .minByOrNull { it.value }
                ?.key
            if (oldest != null) {
                inFlight.remove(oldest)
            }
        }
        inFlight[key.value] = System.currentTimeMillis()
    }

    override fun markCompleted(key: IdempotencyKey) {
        inFlight.remove(key.value)
    }
}
