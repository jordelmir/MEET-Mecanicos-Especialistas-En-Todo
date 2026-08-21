package com.elysium369.meet.core.evair.telemetry

import com.elysium369.meet.core.evair.domain.TelemetryPoint
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe, bounded, O(1) circular ring buffer for storing recent TelemetryPoint samples.
 *
 * When capacity is exceeded, oldest samples are overwritten automatically.
 * Snapshots return a newly allocated list sorted chronologically (oldest -> newest).
 */
class TelemetryRingBuffer(
    val capacity: Int = 1000,
) {
    init {
        require(capacity > 0) { "Capacity must be positive, got: $capacity" }
    }

    private val lock = ReentrantReadWriteLock()
    private val buffer = arrayOfNulls<TelemetryPoint>(capacity)
    private var head = 0
    private var size = 0

    fun add(sample: TelemetryPoint) = lock.write {
        buffer[head] = sample
        head = (head + 1) % capacity
        if (size < capacity) {
            size++
        }
    }

    fun addAll(samples: Collection<TelemetryPoint>) = lock.write {
        for (sample in samples) {
            buffer[head] = sample
            head = (head + 1) % capacity
            if (size < capacity) {
                size++
            }
        }
    }

    val currentSize: Int
        get() = lock.read { size }

    val isFull: Boolean
        get() = lock.read { size == capacity }

    fun clear() = lock.write {
        buffer.fill(null)
        head = 0
        size = 0
    }

    /**
     * Extracts a chronologically ordered snapshot of the buffer.
     * Oldest samples first, newest samples last.
     */
    fun snapshot(): List<TelemetryPoint> = lock.read {
        if (size == 0) return emptyList()

        val result = ArrayList<TelemetryPoint>(size)
        val startIdx = if (size < capacity) 0 else head
        for (i in 0 until size) {
            val idx = (startIdx + i) % capacity
            buffer[idx]?.let { result.add(it) }
        }
        result
    }

    /**
     * Extracts samples within the last [durationMs] based on wall clock timestamp.
     */
    fun snapshotWindow(durationMs: Long, nowMs: Long = System.currentTimeMillis()): List<TelemetryPoint> = lock.read {
        if (size == 0 || durationMs <= 0) return emptyList()
        val cutoff = nowMs - durationMs
        snapshot().filter { it.wallClockTimestampMs >= cutoff }
    }

    /**
     * Returns the most recent sample, or null if empty.
     */
    fun latest(): TelemetryPoint? = lock.read {
        if (size == 0) return null
        val latestIdx = (head - 1 + capacity) % capacity
        buffer[latestIdx]
    }
}
