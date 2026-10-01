package com.elysium.nexus.fabric.canonical

/**
 * §68 Digital Twin History.
 *
 * A ring buffer of state snapshots for a device.
 * The history enables:
 * 1. State diffing (what changed?)
 * 2. Trend detection (is the AC cycling?)
 * 3. Rollback (revert to last known good state)
 * 4. Reconciliation (desired ≠ reported)
 * 5. Audit trail (who changed what, when)
 *
 * The history is immutable: every mutation
 * produces a new [DeviceTwinHistory]. The
 * previous history is the "last known good".
 */
data class DeviceTwinHistory(
    /** The device this history belongs to. */
    val deviceId: DeviceId,
    /** The ring buffer of state snapshots. */
    val snapshots: List<StateSnapshot> = emptyList(),
    /** The desired state (what the system wants). */
    val desiredState: DeviceState = DeviceState.Unknown,
    /** The last confirmed state (device acknowledged). */
    val lastConfirmedState: DeviceState = DeviceState.Unknown,
    /** Wall-clock nanoseconds of the last mutation. */
    val lastMutationNs: Long = 0L
) {
    init {
        require(snapshots.size <= MAX_SNAPSHOTS) {
            "History must not exceed $MAX_SNAPSHOTS snapshots (got ${snapshots.size})."
        }
        require(lastMutationNs >= 0L) {
            "lastMutationNs must be non-negative."
        }
    }

    /** The most recent snapshot, or null if empty. */
    val latest: StateSnapshot? get() = snapshots.lastOrNull()

    /** The most recent reported state. */
    val reportedState: DeviceState get() = latest?.state ?: DeviceState.Unknown

    /**
     * True if the desired state differs from the
     * reported state. The reconciliation engine
     * uses this to decide whether to retry/fallback.
     */
    val isStale: Boolean get() = desiredState != reportedState && desiredState != DeviceState.Unknown

    /**
     * Confidence score [0.0, 1.0] based on:
     * - How recent the last snapshot is
     * - Whether the state has been confirmed
     * - Whether there are reconciliation failures
     */
    val confidence: Double get() {
        if (snapshots.isEmpty()) return 0.0
        val latestSnapshot = latest ?: return 0.0
        val ageNs = System.nanoTime() - latestSnapshot.timestampNs
        val recencyScore = when {
            ageNs < 5_000_000_000L -> 1.0    // < 5s
            ageNs < 30_000_000_000L -> 0.8   // < 30s
            ageNs < 300_000_000_000L -> 0.5  // < 5min
            else -> 0.2
        }
        val confirmedBonus = if (latestSnapshot.isConfirmed) 0.2 else 0.0
        val failurePenalty = if (latestSnapshot.consecutiveFailures > 0) {
            0.1 * latestSnapshot.consecutiveFailures.coerceAtMost(3)
        } else 0.0
        return (recencyScore + confirmedBonus - failurePenalty).coerceIn(0.0, 1.0)
    }

    /**
     * Append a new state snapshot. If the buffer
     * is full, the oldest snapshot is evicted.
     */
    fun append(
        state: DeviceState,
        source: StateSource,
        isConfirmed: Boolean = false,
        metadata: Map<String, String> = emptyMap()
    ): DeviceTwinHistory {
        val snapshot = StateSnapshot(
            timestampNs = System.nanoTime(),
            state = state,
            source = source,
            isConfirmed = isConfirmed,
            metadata = metadata,
            consecutiveFailures = 0
        )
        val newSnapshots = if (snapshots.size >= MAX_SNAPSHOTS) {
            snapshots.drop(1) + snapshot
        } else {
            snapshots + snapshot
        }
        return copy(
            snapshots = newSnapshots,
            lastMutationNs = System.nanoTime()
        )
    }

    /**
     * Mark the latest snapshot as confirmed (device
     * acknowledged the state change).
     */
    fun confirmLatest(): DeviceTwinHistory {
        if (snapshots.isEmpty()) return this
        val updated = snapshots.toMutableList()
        val latest = updated.last()
        updated[updated.lastIndex] = latest.copy(isConfirmed = true)
        return copy(
            snapshots = updated,
            lastConfirmedState = latest.state,
            lastMutationNs = System.nanoTime()
        )
    }

    /**
     * Record a failed reconciliation attempt.
     */
    fun recordFailure(): DeviceTwinHistory {
        if (snapshots.isEmpty()) return this
        val updated = snapshots.toMutableList()
        val latest = updated.last()
        updated[updated.lastIndex] = latest.copy(
            consecutiveFailures = latest.consecutiveFailures + 1
        )
        return copy(
            snapshots = updated,
            lastMutationNs = System.nanoTime()
        )
    }

    /**
     * Update the desired state.
     */
    fun withDesired(state: DeviceState): DeviceTwinHistory =
        copy(desiredState = state, lastMutationNs = System.nanoTime())

    /**
     * Trim the history to [maxSnapshots] entries.
     */
    fun trimmed(maxSnapshots: Int = MAX_SNAPSHOTS): DeviceTwinHistory =
        copy(snapshots = snapshots.takeLast(maxSnapshots))

    companion object {
        /** Maximum snapshots per device. */
        const val MAX_SNAPSHOTS: Int = 100
    }
}

/**
 * A single state snapshot in the history.
 */
data class StateSnapshot(
    /** Wall-clock nanoseconds when this state was observed. */
    val timestampNs: Long,
    /** The device state at this point in time. */
    val state: DeviceState,
    /** Where this state came from. */
    val source: StateSource,
    /** Whether the device confirmed this state. */
    val isConfirmed: Boolean = false,
    /** Arbitrary metadata (e.g. "protocol=WiFi", "latencyMs=42"). */
    val metadata: Map<String, String> = emptyMap(),
    /** How many consecutive reconciliation failures. */
    val consecutiveFailures: Int = 0
)

/**
 * The source of a state observation.
 */
enum class StateSource {
    /** The device reported it directly (subscription, read-back). */
    DeviceReport,
    /** We inferred it from a successful command send. */
    CommandInferred,
    /** The user told us the state (manual override). */
    UserOverride,
    /** The state was restored from history (process death recovery). */
    Restored,
    /** An automation changed the state. */
    Automation,
    /** Unknown source. */
    Unknown
}
