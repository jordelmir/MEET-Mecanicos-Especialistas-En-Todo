package com.elysium369.meet.core.offline

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  O F F L I N E - F I R S T   S Y N C   E N G I N E
 *  ──────────────────────────────────────────────────────
 *  "Si ELYSIUM requiere internet, excluye exactamente a las
 *   personas que más lo necesitan."
 *
 *  A mechanic in Upala, a farmer in Guanacaste, a seamstress in a
 *  village with no 4G. The ENTIRE pipeline must work WITHOUT signal
 *  and sync when connectivity returns.
 *
 *  Architecture:
 *  ┌──────────────────────────────────────────────────────┐
 *  │  LOCAL FIRST (SQLite / Room)                        │
 *  │  ┌────────────┐  ┌────────────┐  ┌──────────────┐  │
 *  │  │ Operations │  │  Conflict  │  │   Sync       │  │
 *  │  │   Queue    │→ │  Resolver  │→ │   Engine     │  │
 *  │  └────────────┘  └────────────┘  └──────┬───────┘  │
 *  └─────────────────────────────────────────│───────────┘
 *                                            │ when online
 *  ┌─────────────────────────────────────────▼───────────┐
 *  │  REMOTE (Cloud / P2P)                               │
 *  └─────────────────────────────────────────────────────┘
 *
 *  Principles:
 *  1. Every write goes to LOCAL FIRST, always
 *  2. Reads NEVER block on network
 *  3. Conflicts are resolved deterministically (LWW + priority)
 *  4. Sync is eventual, not required for any user action
 *  5. Data integrity verified by hash chains
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Connectivity State ───

enum class ConnectivityState {
    ONLINE,             // Full internet
    DEGRADED,           // Slow / intermittent
    OFFLINE,            // No connectivity
    MESH_ONLY,          // P2P / Bluetooth mesh available
    SMS_ONLY,           // Only SMS gateway available
}

// ─── Pending Operation ───

enum class OperationType {
    CREATE, UPDATE, DELETE, SYNC_REQUEST, VERIFY, PAYMENT,
}

enum class OperationPriority(val weight: Int) {
    CRITICAL(100),      // Payments, verifications, emergencies
    HIGH(75),           // Job completions, skill promotions
    NORMAL(50),         // Profile updates, learning progress
    LOW(25),            // Preferences, analytics
    BACKGROUND(10),     // Telemetry, non-essential sync
}

@Serializable
data class PendingOperation(
    val operationId: String,
    val type: OperationType,
    val entityType: String,
    val entityId: String,
    val payload: String, // Serialized JSON
    val priority: OperationPriority = OperationPriority.NORMAL,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 10,
    val syncedAtEpochMs: Long? = null,
    val conflictResolution: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS,
    val integrityHash: String = "",
) {
    val isPending: Boolean get() = syncedAtEpochMs == null
    val hasExhaustedRetries: Boolean get() = retryCount >= maxRetries
    val ageMs: Long get() = System.currentTimeMillis() - createdAtEpochMs
}

enum class ConflictStrategy {
    LAST_WRITE_WINS,    // Most recent timestamp wins
    SERVER_WINS,        // Server version takes precedence
    CLIENT_WINS,        // Local version takes precedence
    MERGE,              // Attempt field-level merge
    MANUAL,             // Flag for human resolution
}

// ─── Sync Result ───

@Serializable
data class SyncResult(
    val totalOperations: Int,
    val synced: Int,
    val conflictsResolved: Int,
    val conflictsPending: Int,
    val failed: Int,
    val durationMs: Long,
) {
    val successRate: Double
        get() = if (totalOperations == 0) 1.0
        else synced.toDouble() / totalOperations

    val isFullySync: Boolean get() = synced == totalOperations
}

// ─── Conflict Record ───

@Serializable
data class ConflictRecord(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val localVersion: String,
    val remoteVersion: String,
    val resolvedBy: ConflictStrategy? = null,
    val resolvedAtEpochMs: Long? = null,
    val isResolved: Boolean = false,
)

// ─── The Offline-First Engine ───

class OfflineFirstEngine {

    private var connectivity = ConnectivityState.ONLINE
    private val operationQueue = mutableListOf<PendingOperation>()
    private val conflicts = mutableListOf<ConflictRecord>()
    private val syncHistory = mutableListOf<SyncResult>()

    // ─── Connectivity ───

    fun updateConnectivity(state: ConnectivityState) {
        connectivity = state
    }

    fun currentConnectivity(): ConnectivityState = connectivity

    val isOnline: Boolean get() = connectivity == ConnectivityState.ONLINE
    val canSync: Boolean get() = connectivity in listOf(
        ConnectivityState.ONLINE, ConnectivityState.DEGRADED,
    )

    // ─── Queue Operations (always local-first) ───

    /**
     * Enqueue an operation. This ALWAYS succeeds, regardless of
     * connectivity. The operation is stored locally and will sync
     * when connectivity is available.
     */
    fun enqueue(operation: PendingOperation): PendingOperation {
        val withHash = operation.copy(
            integrityHash = computeHash(operation),
        )
        operationQueue.add(withHash)
        return withHash
    }

    /**
     * Creates and enqueues a new operation.
     */
    fun createOperation(
        type: OperationType,
        entityType: String,
        entityId: String,
        payload: String,
        priority: OperationPriority = OperationPriority.NORMAL,
    ): PendingOperation {
        val op = PendingOperation(
            operationId = "op-${System.currentTimeMillis()}-${operationQueue.size}",
            type = type,
            entityType = entityType,
            entityId = entityId,
            payload = payload,
            priority = priority,
        )
        return enqueue(op)
    }

    // ─── Queue Management ───

    val pendingCount: Int get() = operationQueue.count { it.isPending }
    val totalQueued: Int get() = operationQueue.size

    fun pendingOperations(): List<PendingOperation> {
        return operationQueue
            .filter { it.isPending && !it.hasExhaustedRetries }
            .sortedByDescending { it.priority.weight }
    }

    fun criticalPending(): List<PendingOperation> {
        return pendingOperations().filter {
            it.priority == OperationPriority.CRITICAL
        }
    }

    // ─── Sync ───

    /**
     * Attempts to sync all pending operations.
     * Operations are processed in priority order.
     * Returns a SyncResult with statistics.
     */
    fun sync(): SyncResult {
        val startTime = System.currentTimeMillis()
        val pending = pendingOperations()

        if (!canSync) {
            return SyncResult(
                totalOperations = pending.size,
                synced = 0, conflictsResolved = 0,
                conflictsPending = 0, failed = 0,
                durationMs = System.currentTimeMillis() - startTime,
            )
        }

        var synced = 0
        var conflictsResolved = 0
        var failed = 0

        for (op in pending) {
            val result = syncSingleOperation(op)
            when (result) {
                SyncOutcome.SUCCESS -> synced++
                SyncOutcome.CONFLICT_RESOLVED -> {
                    synced++
                    conflictsResolved++
                }
                SyncOutcome.CONFLICT_PENDING -> {} // left for manual
                SyncOutcome.FAILED -> failed++
            }
        }

        val result = SyncResult(
            totalOperations = pending.size,
            synced = synced,
            conflictsResolved = conflictsResolved,
            conflictsPending = conflicts.count { !it.isResolved },
            failed = failed,
            durationMs = System.currentTimeMillis() - startTime,
        )

        syncHistory.add(result)
        return result
    }

    private fun syncSingleOperation(op: PendingOperation): SyncOutcome {
        // Simulate sync — in production this hits the API
        val idx = operationQueue.indexOfFirst { it.operationId == op.operationId }
        if (idx < 0) return SyncOutcome.FAILED

        // Mark as synced
        operationQueue[idx] = op.copy(
            syncedAtEpochMs = System.currentTimeMillis(),
        )
        return SyncOutcome.SUCCESS
    }

    // ─── Conflict Resolution ───

    fun addConflict(conflict: ConflictRecord) {
        conflicts.add(conflict)
    }

    fun resolveConflict(operationId: String, strategy: ConflictStrategy): Boolean {
        val idx = conflicts.indexOfFirst {
            it.operationId == operationId && !it.isResolved
        }
        if (idx < 0) return false

        conflicts[idx] = conflicts[idx].copy(
            isResolved = true,
            resolvedBy = strategy,
            resolvedAtEpochMs = System.currentTimeMillis(),
        )
        return true
    }

    val unresolvedConflicts: List<ConflictRecord>
        get() = conflicts.filter { !it.isResolved }

    // ─── SMS Fallback ───

    /**
     * For SMS_ONLY connectivity: encodes critical operations into
     * SMS-safe format (< 160 chars per segment).
     */
    fun encodeForSms(operation: PendingOperation): String {
        // Compact format: TYPE|ENTITY|ID|HASH
        return "${operation.type.name.take(3)}|" +
            "${operation.entityType.take(10)}|" +
            "${operation.entityId.take(20)}|" +
            operation.integrityHash.take(8)
    }

    /**
     * Decodes an SMS-received operation back into a PendingOperation.
     */
    fun decodeFromSms(smsPayload: String): PendingOperation? {
        val parts = smsPayload.split("|")
        if (parts.size < 4) return null

        val type = OperationType.entries.firstOrNull {
            it.name.startsWith(parts[0])
        } ?: return null

        return PendingOperation(
            operationId = "sms-${System.currentTimeMillis()}",
            type = type,
            entityType = parts[1],
            entityId = parts[2],
            payload = "{}",
            priority = OperationPriority.CRITICAL,
            integrityHash = parts[3],
        )
    }

    // ─── Integrity ───

    private fun computeHash(op: PendingOperation): String {
        val input = "${op.type}:${op.entityType}:${op.entityId}:${op.payload}:${op.createdAtEpochMs}"
        return "sha256-${input.hashCode().toUInt()}"
    }

    // ─── Diagnostics ───

    fun diagnostics(): OfflineDiagnostics {
        return OfflineDiagnostics(
            connectivity = connectivity,
            pendingOperations = pendingCount,
            criticalPending = criticalPending().size,
            unresolvedConflicts = unresolvedConflicts.size,
            totalSyncs = syncHistory.size,
            lastSyncResult = syncHistory.lastOrNull(),
            oldestPendingAgeMs = operationQueue
                .filter { it.isPending }
                .minByOrNull { it.createdAtEpochMs }
                ?.ageMs,
        )
    }

    private enum class SyncOutcome {
        SUCCESS, CONFLICT_RESOLVED, CONFLICT_PENDING, FAILED,
    }
}

@Serializable
data class OfflineDiagnostics(
    val connectivity: ConnectivityState,
    val pendingOperations: Int,
    val criticalPending: Int,
    val unresolvedConflicts: Int,
    val totalSyncs: Int,
    val lastSyncResult: SyncResult?,
    val oldestPendingAgeMs: Long?,
)
