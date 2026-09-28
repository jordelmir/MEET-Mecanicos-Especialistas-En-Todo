package com.elysium.nexus.core.budgets

/**
 * §75-78 Performance / Battery / Memory Budgets.
 *
 * Defines hard budgets for system resources.
 * Every subsystem must respect these budgets.
 *
 * ## Input Path Budget
 * ```
 * touch → canonical input < 2 ms
 * canonical → route < 1 ms
 * ```
 *
 * ## HID Budget
 * ```
 * low single-digit ms where platform permits
 * ```
 *
 * ## LAN Budget
 * ```
 * p50 < 50 ms
 * p95 < 150 ms
 * ```
 *
 * ## Battery Budget
 * Discovery cannot run aggressively permanently.
 * States: ACTIVE_CONTROL, PASSIVE_MONITOR, BACKGROUND, IDLE
 *
 * ## Memory Budget
 * Don't load full catalog in RAM.
 * SQLite indexed queries, candidate paging,
 * lazy signals, bounded cache.
 */
object PerformanceBudgets {

    // ── Input Path ──────────────────────────────

    /** Maximum touch-to-canonical latency in nanoseconds. */
    const val TOUCH_TO_CANonical_NS: Long = 2_000_000L // 2ms

    /** Maximum canonical-to-route latency in nanoseconds. */
    const val CANONICAL_TO_ROUTE_NS: Long = 1_000_000L // 1ms

    /** Maximum total input path latency in nanoseconds. */
    const val TOTAL_INPUT_PATH_NS: Long = TOUCH_TO_CANonical_NS + CANONICAL_TO_ROUTE_NS

    // ── HID Path ────────────────────────────────

    /** Maximum HID report latency in milliseconds. */
    const val HID_MAX_LATENCY_MS: Long = 5L

    // ── LAN Path ────────────────────────────────

    /** LAN command latency p50 target in milliseconds. */
    const val LAN_P50_TARGET_MS: Long = 50L

    /** LAN command latency p95 target in milliseconds. */
    const val LAN_P95_TARGET_MS: Long = 150L

    /** LAN command latency hard maximum in milliseconds. */
    const val LAN_HARD_MAX_MS: Long = 500L

    // ── IR Path ─────────────────────────────────

    /** IR first candidate success rate target. */
    const val IR_FIRST_CANDIDATE_TARGET: Double = 0.80

    /** IR top-3 candidate success rate target. */
    const val IR_TOP3_TARGET: Double = 0.95

    /** IR top-5 candidate success rate target. */
    const val IR_TOP5_TARGET: Double = 0.98

    // ── Discovery ───────────────────────────────

    /** Maximum discovery scan duration in milliseconds. */
    const val DISCOVERY_MAX_SCAN_MS: Long = 15_000L

    /** Maximum discovery scan duration for quick scan in milliseconds. */
    const val DISCOVERY_QUICK_SCAN_MS: Long = 5_000L

    // ── Crash-Free Sessions ─────────────────────

    /** Target crash-free session rate. */
    const val CRASH_FREE_SESSION_TARGET: Double = 0.999

    // ── Setup Time ──────────────────────────────

    /** Median known-TV setup time target in seconds. */
    const val KNOWN_TV_SETUP_TARGET_S: Long = 20L

    // ── Reconnection ────────────────────────────

    /** Known device reconnect success rate target. */
    const val RECONNECT_SUCCESS_TARGET: Double = 0.995
}

/**
 * Battery state budgets for different operation modes.
 */
object BatteryBudgets {

    /** Active control mode: full functionality. */
    data class ActiveControlBudget(
        val discoveryIntervalMs: Long = 1_000L,
        val statePollingIntervalMs: Long = 1_000L,
        val bleScanDutyCycle: Double = 1.0,
        val maxConcurrentConnections: Int = 10
    )

    /** Passive monitoring mode: reduced activity. */
    data class PassiveMonitorBudget(
        val discoveryIntervalMs: Long = 10_000L,
        val statePollingIntervalMs: Long = 30_000L,
        val bleScanDutyCycle: Double = 0.1,
        val maxConcurrentConnections: Int = 5
    )

    /** Background mode: minimal activity. */
    data class BackgroundBudget(
        val discoveryIntervalMs: Long = 60_000L,
        val statePollingIntervalMs: Long = 300_000L,
        val bleScanDutyCycle: Double = 0.01,
        val maxConcurrentConnections: Int = 2
    )

    /** Idle mode: almost no activity. */
    data class IdleBudget(
        val discoveryIntervalMs: Long = 300_000L,
        val statePollingIntervalMs: Long = 600_000L,
        val bleScanDutyCycle: Double = 0.0,
        val maxConcurrentConnections: Int = 0
    )
}

/**
 * Memory budgets for different subsystems.
 */
object MemoryBudgets {

    /** Maximum number of devices to hold in memory. */
    const val MAX_IN_MEMORY_DEVICES: Int = 200

    /** Maximum number of IR signals to cache. */
    const val MAX_IR_SIGNAL_CACHE: Int = 1_000

    /** Maximum number of flight recorder entries. */
    const val MAX_FLIGHT_RECORDER_ENTRIES: Int = 500

    /** Maximum number of evidence store entries. */
    const val MAX_EVIDENCE_STORE_ENTRIES: Int = 1_000

    /** Maximum number of recorded actions. */
    const val MAX_RECORDED_ACTIONS: Int = 10_000

    /** Maximum catalog page size for lazy loading. */
    const val CATALOG_PAGE_SIZE: Int = 50

    /** Maximum device twin history snapshots per device. */
    const val MAX_TWIN_HISTORY_SNAPSHOTS: Int = 100

    /** Maximum credential vault entries. */
    const val MAX_CREDENTIAL_ENTRIES: Int = 100
}

/**
 * Performance measurement helper.
 */
class PerformanceTracker {
    private val measurements = mutableMapOf<String, MutableList<Long>>()

    /**
     * Record a latency measurement.
     */
    fun record(operation: String, latencyNs: Long) {
        measurements.getOrPut(operation) { mutableListOf() }.add(latencyNs)
    }

    /**
     * Get p50 latency for an operation in nanoseconds.
     */
    fun p50(operation: String): Long {
        val sorted = measurements[operation]?.sorted() ?: return 0
        if (sorted.isEmpty()) return 0
        return sorted[sorted.size / 2]
    }

    /**
     * Get p95 latency for an operation in nanoseconds.
     */
    fun p95(operation: String): Long {
        val sorted = measurements[operation]?.sorted() ?: return 0
        if (sorted.isEmpty()) return 0
        val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        return sorted[index]
    }

    /**
     * Get average latency for an operation in nanoseconds.
     */
    fun average(operation: String): Double {
        val values = measurements[operation] ?: return 0.0
        if (values.isEmpty()) return 0.0
        return values.average()
    }

    /**
     * Get count of measurements for an operation.
     */
    fun count(operation: String): Int = measurements[operation]?.size ?: 0

    /**
     * Check if an operation meets its latency budget.
     */
    fun meetsBudget(operation: String, budgetNs: Long): Boolean {
        return p95(operation) <= budgetNs
    }

    /**
     * Clear all measurements.
     */
    fun clear() = measurements.clear()

    /**
     * Clear measurements for a specific operation.
     */
    fun clear(operation: String) = measurements.remove(operation)
}
