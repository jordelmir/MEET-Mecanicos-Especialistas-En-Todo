package com.elysium.nexus.core.latency

/**
 * Rolling-window latency tracker.
 *
 * Per `MASTER_ORDER.md` §30, the platform must measure:
 *
 * ```
 * p50, p90, p95, p99, jitter, packet loss, coalescing, queue depth, clock offset
 * ```
 *
 * The latency tracker is the cheapest possible first cut: a
 * rolling window of latency samples in nanoseconds, with
 * percentile queries on demand. It is thread-safe; the view
 * thread (which records T0) and the engine's commit path
 * (which records T2) can both write to it without
 * coordination.
 *
 * The window size is bounded (default 1024) so the tracker
 * is allocation-free in steady state. The window is small
 * enough to keep memory predictable (8 KB at 1024 samples)
 * and large enough that the percentiles are statistically
 * meaningful.
 *
 * ## Why not Kotest / kotlin.test's stdlib percentile
 *
 * Kotest would add a third-party dep we have not yet earned.
 * The percentile calculation here is a 5-line sort + index
 * lookup; the test pins the algorithm. A property test
 * (Phase 0.8) feeds 1000 random samples and asserts the
 * invariants §30 promises: p50 < 4 ms for the touch
 * processing path.
 *
 * ## Why a separate file
 *
 * The tracker is the smallest, most testable piece of the
 * §30 budget. It lives in `core/latency/` and has no
 * dependency on the engine, the dispatcher, or the view.
 * The integration with the engine is in
 * [CanonicalInputEngine]; the integration with the view is
 * in [com.elysium.nexus.input.TouchSurfaceView]. Each is
 * a one-line wire-up; the tracker itself is a data
 * structure.
 */
class LatencyTracker(
    private val maxSamples: Int = 1024,
    private val clock: () -> Long = { System.nanoTime() }
) {

    private val samples: ArrayDeque<Long> = ArrayDeque(maxSamples)
    private val lock = Any()

    init {
        require(maxSamples > 0) {
            "maxSamples must be positive (got $maxSamples)."
        }
    }

    /**
     * Record a latency sample. The latency is expected to
     * be a non-negative value in nanoseconds.
     *
     * Thread-safe: multiple writers (e.g. the view thread
     * and the engine's commit path) can call this
     * concurrently.
     */
    fun record(latencyNs: Long) {
        synchronized(lock) {
            if (samples.size >= maxSamples) {
                samples.removeFirst()
            }
            samples.addLast(latencyNs)
        }
    }

    /**
     * Convenience: record the diff between [startNs] and
     * "now" (according to the injected [clock]). Negative
     * diffs (clock skew, a recorder that fired late) are
     * clamped to zero.
     */
    fun recordSince(startNs: Long) {
        val now = clock()
        val diff = now - startNs
        record(if (diff < 0) 0L else diff)
    }

    /**
     * @return a [Snapshot] of the current window's
     *   percentiles. The snapshot is point-in-time: a
     *   subsequent [record] does not retroactively change
     *   the snapshot.
     *
     * The snapshot is `EMPTY` (zeros) when no samples have
     * been recorded. The percentiles are `0` for an empty
     * window; consumers should check [Snapshot.count]
     * before reporting.
     */
    fun snapshot(): Snapshot = synchronized(lock) {
        if (samples.isEmpty()) return Snapshot.EMPTY
        val sorted = samples.toLongArray().also { java.util.Arrays.sort(it) }
        Snapshot(
            count = sorted.size,
            min = sorted.first(),
            max = sorted.last(),
            p50 = percentileLinear(sorted, 0.50),
            p90 = percentileLinear(sorted, 0.90),
            p95 = percentileLinear(sorted, 0.95),
            p99 = percentileLinear(sorted, 0.99)
        )
    }

    /**
     * Reset the tracker. Used by the engine on every
     * state-machine transition out of `Active` so a stale
     * window does not bias the percentiles across
     * reconnections.
     */
    fun clear() {
        synchronized(lock) {
            samples.clear()
        }
    }

    /**
     * The number of samples currently in the window. Used
     * by tests to assert the rolling behaviour.
     */
    fun size(): Int = synchronized(lock) { samples.size }

    /**
     * Linear-interpolation percentile of a sorted
     * [sorted] array. The "type 7" quantile (R's default,
     * NumPy's default, Excel's PERCENTILE.INC). For a
     * percentile [p] in `[0, 1]` of an array of size
     * [size]:
     *
     * ```
     * rank = p * (size - 1)
     * lower = floor(rank)
     * upper = ceil(rank)
     * if lower == upper:
     *     result = sorted[lower]
     * else:
     *     fraction = rank - lower
     *     result = sorted[lower] + fraction * (sorted[upper] - sorted[lower])
     * ```
     *
     * Linear interpolation gives a more useful answer
     * than the "nearest rank" method for small samples
     * (e.g. p50 of `[0, 500]` is `250`, not `0`). It is
     * also what every standard stats library uses, so
     * the numbers we report are comparable to other
     * tools.
     */
    private fun percentileLinear(sorted: LongArray, p: Double): Long {
        require(p in 0.0..1.0) { "p must be in [0, 1] (got $p)." }
        val size = sorted.size
        val rank = p * (size - 1)
        val lower = rank.toInt()
        val upper = (lower + 1).coerceAtMost(size - 1)
        return if (lower == upper) {
            sorted[lower]
        } else {
            val fraction = rank - lower
            val diff = sorted[upper] - sorted[lower]
            sorted[lower] + (diff * fraction).toLong()
        }
    }

    /**
     * A point-in-time view of the tracker's percentiles.
     * All values are in nanoseconds. `count` is the number
     * of samples that contributed; consumers should treat
     * `count == 0` as "no data".
     */
    data class Snapshot(
        val count: Int,
        val min: Long,
        val max: Long,
        val p50: Long,
        val p90: Long,
        val p95: Long,
        val p99: Long
    ) {
        companion object {
            val EMPTY: Snapshot = Snapshot(
                count = 0,
                min = 0L,
                max = 0L,
                p50 = 0L,
                p90 = 0L,
                p95 = 0L,
                p99 = 0L
            )
        }
    }
}
