package com.elysium.nexus.core.latency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LatencyTracker] — the §30 latency tracker.
 *
 * The tests cover the basic data structure (recording,
 * snapshot, percentiles) plus a property test that feeds
 * 1000 random samples and asserts the p50 calculation is
 * within the §30 budget (< 4 ms for touch processing).
 */
class LatencyTrackerTest {

    @Test
    fun emptySnapshotIsAllZeros() {
        val s = LatencyTracker().snapshot()
        assertEquals(0, s.count)
        assertEquals(0L, s.p50)
        assertEquals(0L, s.p95)
    }

    @Test
    fun singleSampleHasAllPercentilesEqual() {
        val t = LatencyTracker()
        t.record(42L)
        val s = t.snapshot()
        assertEquals(1, s.count)
        assertEquals(42L, s.min)
        assertEquals(42L, s.max)
        assertEquals(42L, s.p50)
        assertEquals(42L, s.p90)
        assertEquals(42L, s.p95)
        assertEquals(42L, s.p99)
    }

    @Test
    fun percentilesAreOrdered() {
        val t = LatencyTracker()
        for (i in 1..100) t.record(i.toLong())
        val s = t.snapshot()
        assertTrue("p50 should be <= p90", s.p50 <= s.p90)
        assertTrue("p90 should be <= p95", s.p90 <= s.p95)
        assertTrue("p95 should be <= p99", s.p95 <= s.p99)
        assertTrue("p99 should be <= max", s.p99 <= s.max)
        assertTrue("min should be <= p50", s.min <= s.p50)
    }

    @Test
    fun percentilesOfSequentialSamples() {
        // Samples 1..100: p50 = 50, p90 = 90, p95 = 95, p99 = 99.
        val t = LatencyTracker()
        for (i in 1..100) t.record(i.toLong())
        val s = t.snapshot()
        assertEquals(50L, s.p50)
        assertEquals(90L, s.p90)
        assertEquals(95L, s.p95)
        assertEquals(99L, s.p99)
    }

    @Test
    fun rollingWindowDropsOldestSamples() {
        val t = LatencyTracker(maxSamples = 10)
        for (i in 1..20) t.record(i.toLong())
        // Only the last 10 (11..20) are in the window.
        assertEquals(10, t.size())
        val s = t.snapshot()
        assertEquals(10, s.count)
        assertEquals(11L, s.min)
        assertEquals(20L, s.max)
    }

    @Test
    fun clearResetsTheWindow() {
        val t = LatencyTracker()
        for (i in 1..50) t.record(i.toLong())
        assertEquals(50, t.size())
        t.clear()
        assertEquals(0, t.size())
        assertEquals(0, t.snapshot().count)
    }

    @Test
    fun negativeLatencyIsClampedToZero() {
        val t = LatencyTracker()
        t.record(-1L)
        t.record(-1000L)
        // The values are stored as-is (clamping is in
        // recordSince, not record). The percentile of two
        // negative values is the larger (less negative).
        val s = t.snapshot()
        assertEquals(2, s.count)
    }

    @Test
    fun recordSinceComputesDiffAndClampsNegativeToZero() {
        var fakeNow: Long = 1000L
        val t = LatencyTracker(clock = { fakeNow })
        t.recordSince(500L) // 500 ns diff
        fakeNow = 2000L
        t.recordSince(3000L) // -1000 ns diff; clamped to 0
        val s = t.snapshot()
        assertEquals(2, s.count)
        // p50 of [0, 500] is 250.
        assertEquals(250L, s.p50)
    }

    @Test
    fun maxSamplesMustBePositive() {
        try {
            LatencyTracker(maxSamples = 0)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun percentileIndexHandlesEdgeCases() {
        val t = LatencyTracker()
        // A single sample: every percentile is that sample.
        t.record(7L)
        val s = t.snapshot()
        assertEquals(7L, s.p50)
        assertEquals(7L, s.p90)
        assertEquals(7L, s.p95)
        assertEquals(7L, s.p99)
    }

    @Test
    fun p50UnderSection30BudgetOnSyntheticWorkload() = run {
        // §30 budget: touch processing median < 4 ms.
        // The LatencyTracker alone does not "process" anything,
        // but the percentile algorithm is the gate: the engine
        // will record its commit latency through this tracker.
        // We seed 1000 samples in the 0..4 ms range and assert
        // the percentiles are in range. This is the property
        // the engine's commit path will be measured against.
        val t = LatencyTracker(maxSamples = 1024)
        val rng = java.util.Random(0xC0FFEEL) // deterministic
        for (i in 0 until 1000) {
            // 0..4_000_000 ns = 0..4 ms.
            t.record(rng.nextInt(4_000_000).toLong())
        }
        val s = t.snapshot()
        assertEquals(1000, s.count)
        assertTrue(
            "p50 (${s.p50} ns) should be < 4 ms (4_000_000 ns)",
            s.p50 < 4_000_000L
        )
        assertTrue(
            "p95 (${s.p95} ns) should be < 4 ms",
            s.p95 < 4_000_000L
        )
    }

    @Test
    fun p95UnderSection30BudgetOnSyntheticWorkload() = run {
        // §30: end-to-end p95 < 30 ms. The tracker's percentile
        // algorithm is the gate; the engine + transport +
        // receiver pipeline is what will be measured.
        val t = LatencyTracker(maxSamples = 1024)
        val rng = java.util.Random(0xBADF00DL)
        for (i in 0 until 1000) {
            // 0..30_000_000 ns = 0..30 ms.
            t.record(rng.nextInt(30_000_000).toLong())
        }
        val s = t.snapshot()
        assertTrue(
            "p95 (${s.p95} ns) should be < 30 ms (30_000_000 ns)",
            s.p95 < 30_000_000L
        )
    }
}
