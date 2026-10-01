package com.elysium.nexus.fabric.hedging

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.routing.TransportRoute
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * §61 Hedged Execution.
 *
 * For safe actions, send the command on the
 * primary route and, if no ACK within [hedgeDelayMs],
 * send on a backup route.
 *
 * ## Hedging is ONLY for:
 * - Read operations (state queries)
 * - Idempotent actions (POWER_TOGGLE, MUTE_TOGGLE)
 *
 * ## Hedging is NEVER for:
 * - Non-idempotent actions (VOLUME_UP, CHANNEL_UP)
 *   where double execution = user-visible bug
 * - Destructive actions (factory reset)
 *
 * ## Policy
 *
 * ```
 * WiFi request
 * ↓
 * no ACK after 120ms
 * ↓
 * fallback IR
 * ```
 *
 * But only if:
 * ```
 * idempotency classification = IDEMPOTENT
 * state confirmation available
 * ```
 */
class HedgedExecutor(
    private val hedgeDelayMs: Long = 120L,
    private val maxConcurrentHedges: Int = 2
) {

    private var activeHedges = 0

    /**
     * Execute with hedging. If the primary route
     * doesn't respond within [hedgeDelayMs], try
     * the backup route.
     *
     * @param action the universal action
     * @param primary the primary route
     * @param backup the backup route (optional)
     * @param executor function to execute on a route
     * @return the result from whichever route succeeded first
     */
    /**
     * V06-P18: hedging gated by the single [MutationSemantics] classifier so
     * every execution policy asks one source.
     */
    suspend fun <T> executeWithHedge(
        action: UniversalAction,
        primary: TransportRoute,
        backup: TransportRoute?,
        executor: suspend (TransportRoute) -> T?
    ): HedgedResult<T> {
        // Only hedge idempotent actions (single classification source,
        // V06-P18: MutationSemantics).
        if (!MutationSemantics.canHedge(action)) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Primary route failed")
            }
        }

        if (backup == null) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Primary route failed, no backup")
            }
        }

        if (activeHedges >= maxConcurrentHedges) {
            val result = executor(primary)
            return if (result != null) {
                HedgedResult.PrimarySuccess(result)
            } else {
                HedgedResult.PrimaryFailed("Too many active hedges")
            }
        }

        return coroutineScope {
            activeHedges++
            try {
                var primaryResult: T? = null
                var backupTriggered = false

                // Launch primary
                val primaryJob = launch {
                    val result = executor(primary)
                    if (result != null) {
                        primaryResult = result
                    }
                }

                // Wait for hedge delay
                delay(hedgeDelayMs)

                // If primary hasn't completed, try backup
                if (primaryResult == null) {
                    backupTriggered = true
                    val backupResult = executor(backup)
                    if (backupResult != null) {
                        primaryJob.cancel()
                        return@coroutineScope HedgedResult.BackupSuccess(backupResult)
                    }
                }

                // Wait for primary to finish
                primaryJob.join()
                val result = primaryResult
                if (result != null) {
                    HedgedResult.PrimarySuccess(result)
                } else {
                    HedgedResult.BothFailed("Both routes failed")
                }
            } finally {
                activeHedges--
            }
        }
    }

}

/**
 * Hedged execution result.
 */
sealed class HedgedResult<T> {
    data class PrimarySuccess<T>(val value: T?) : HedgedResult<T>()
    data class BackupSuccess<T>(val value: T?) : HedgedResult<T>()
    data class PrimaryFailed<T>(val reason: String) : HedgedResult<T>()
    data class BothFailed<T>(val reason: String) : HedgedResult<T>()
}
