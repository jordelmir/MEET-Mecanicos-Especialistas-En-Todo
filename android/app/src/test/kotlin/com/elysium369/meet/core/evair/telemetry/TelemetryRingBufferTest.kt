package com.elysium369.meet.core.evair.telemetry

import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRingBufferTest {

    @Test
    fun `empty buffer has size zero and empty snapshot`() {
        val buffer = TelemetryRingBuffer(capacity = 5)
        assertEquals(0, buffer.currentSize)
        assertFalse(buffer.isFull)
        assertTrue(buffer.snapshot().isEmpty())
        assertNull(buffer.latest())
    }

    @Test
    fun `adding samples respects capacity and overwrites oldest`() {
        val buffer = TelemetryRingBuffer(capacity = 3)
        val p1 = createPoint("010C", 1000.0, 100L)
        val p2 = createPoint("010C", 1100.0, 200L)
        val p3 = createPoint("010C", 1200.0, 300L)
        val p4 = createPoint("010C", 1300.0, 400L)

        buffer.add(p1)
        buffer.add(p2)
        buffer.add(p3)

        assertEquals(3, buffer.currentSize)
        assertTrue(buffer.isFull)
        assertEquals(p3, buffer.latest())

        // Overwrite oldest (p1)
        buffer.add(p4)
        assertEquals(3, buffer.currentSize)
        assertEquals(p4, buffer.latest())

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(listOf(p2, p3, p4), snapshot)
    }

    @Test
    fun `snapshot window extracts only within cutoff`() {
        val buffer = TelemetryRingBuffer(capacity = 10)
        val now = 10000L
        buffer.add(createPoint("010C", 1000.0, now - 5000L)) // 5s ago
        buffer.add(createPoint("010C", 1100.0, now - 3000L)) // 3s ago
        buffer.add(createPoint("010C", 1200.0, now - 1000L)) // 1s ago

        // Request 2-second window
        val window = buffer.snapshotWindow(durationMs = 2000L, nowMs = now)
        assertEquals(1, window.size)
        assertEquals(1200.0, window.first().value, 0.001)
    }

    @Test
    fun `clear resets buffer state completely`() {
        val buffer = TelemetryRingBuffer(capacity = 5)
        buffer.add(createPoint("010C", 1000.0, 100L))
        buffer.add(createPoint("010C", 1100.0, 200L))
        assertEquals(2, buffer.currentSize)

        buffer.clear()
        assertEquals(0, buffer.currentSize)
        assertTrue(buffer.snapshot().isEmpty())
        assertNull(buffer.latest())
    }

    private fun createPoint(pid: String, value: Double, timestampMs: Long): TelemetryPoint {
        return TelemetryPoint(
            monotonicTimestampNs = timestampMs * 1_000_000L,
            wallClockTimestampMs = timestampMs,
            pid = pid,
            value = value,
            unit = "RPM",
            quality = DataQuality.GOOD
        )
    }
}
