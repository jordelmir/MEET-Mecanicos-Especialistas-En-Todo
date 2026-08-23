package com.elysium369.meet.core.evair.telemetry

import android.os.SystemClock
import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.QualitySummary
import com.elysium369.meet.core.evair.domain.SignalFeatures
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import com.elysium369.meet.core.evair.domain.TelemetryWindow
import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.TelemetryQuality
import com.elysium369.meet.core.obd.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TelemetryCollector — Passively observes ObdSession telemetry streams and feeds high-frequency ring buffers.
 *
 * Provides bounded temporal window slices and statistical feature extraction
 * without interrupting or altering the primary OBD polling loop.
 */
@Singleton
class TelemetryCollector @Inject constructor(
    private val obdSession: ObdSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ringBuffers = ConcurrentHashMap<String, TelemetryRingBuffer>()
    private val parameterNames = ConcurrentHashMap<String, String>()
    private val parameterUnits = ConcurrentHashMap<String, String>()
    private val lastRecordedMonotonicMs = ConcurrentHashMap<String, Long>()

    init {
        startObservation()
    }

    private fun startObservation() {
        scope.launch {
            obdSession.state.combine(obdSession.telemetrySamples) { state, samples -> state to samples }
                .collectLatest { (state, samplesMap) ->
                if (state != ObdState.CONNECTED) {
                    clear()
                    return@collectLatest
                }
                val nowWallMs = System.currentTimeMillis()
                val nowMonoNs = SystemClock.elapsedRealtimeNanos()

                for (sample in samplesMap.values.distinctBy { it.pid }) {
                    val previousTimestamp = lastRecordedMonotonicMs.put(sample.pid, sample.timestampMonotonicMs)
                    if (previousTimestamp == sample.timestampMonotonicMs) continue
                    recordSample(sample.pid, sample, nowWallMs, nowMonoNs)
                }
            }
        }
    }

    fun recordSample(
        pid: String,
        sample: TelemetrySample,
        wallClockMs: Long = System.currentTimeMillis(),
        monotonicNs: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        val value = sample.value ?: return
        parameterNames[pid] = sample.name
        parameterUnits[pid] = sample.unit

        val quality = when (sample.quality) {
            TelemetryQuality.VALID -> DataQuality.GOOD
            TelemetryQuality.OUT_OF_RANGE -> DataQuality.INVALID
            TelemetryQuality.STALE -> DataQuality.STALE
            TelemetryQuality.SIMULATED, TelemetryQuality.MANUAL -> DataQuality.ESTIMATED
            else -> DataQuality.INVALID
        }

        val point = TelemetryPoint(
            monotonicTimestampNs = monotonicNs,
            wallClockTimestampMs = wallClockMs,
            pid = pid,
            value = value,
            unit = sample.unit,
            quality = quality
        )

        val buffer = ringBuffers.getOrPut(pid) { TelemetryRingBuffer(capacity = 1200) }
        buffer.add(point)
    }

    /**
     * Retrieves a bounded TelemetryWindow for a given PID over the requested duration in seconds.
     */
    fun getTelemetryWindow(pid: String, durationSeconds: Int = 30): TelemetryWindow {
        val buffer = ringBuffers[pid]
        val durationMs = durationSeconds.toLong() * 1000L
        val nowMs = System.currentTimeMillis()
        val samples = buffer?.snapshotWindow(durationMs, nowMs) ?: emptyList()

        var good = 0
        var est = 0
        var stale = 0
        var inv = 0

        for (s in samples) {
            when (s.quality) {
                DataQuality.GOOD -> good++
                DataQuality.ESTIMATED -> est++
                DataQuality.STALE -> stale++
                DataQuality.INVALID -> inv++
            }
        }

        val startMs = if (samples.isNotEmpty()) samples.first().wallClockTimestampMs else nowMs - durationMs
        val endMs = if (samples.isNotEmpty()) samples.last().wallClockTimestampMs else nowMs

        return TelemetryWindow(
            pid = pid,
            parameterName = parameterNames[pid] ?: pid,
            unit = parameterUnits[pid] ?: "",
            startTimestampMs = startMs,
            endTimestampMs = endMs,
            durationMs = (endMs - startMs).coerceAtLeast(0L),
            sampleCount = samples.size,
            samples = samples,
            qualitySummary = QualitySummary(
                goodCount = good,
                estimatedCount = est,
                staleCount = stale,
                invalidCount = inv
            )
        )
    }

    /**
     * Extracts statistical signal features for a given PID over the duration.
     */
    fun getSignalFeatures(pid: String, durationSeconds: Int = 30): SignalFeatures {
        val window = getTelemetryWindow(pid, durationSeconds)
        return FeatureExtractor.extract(window)
    }

    /**
     * Returns the latest recorded TelemetryPoint for a given PID.
     */
    fun getLatestPoint(pid: String): TelemetryPoint? {
        return ringBuffers[pid]?.latest()
    }

    /**
     * Returns snapshots for all active PID buffers over the given duration.
     */
    fun getAllRecentWindows(durationSeconds: Int = 30): Map<String, List<TelemetryPoint>> {
        val durationMs = durationSeconds.toLong() * 1000L
        val nowMs = System.currentTimeMillis()
        val result = mutableMapOf<String, List<TelemetryPoint>>()
        for ((pid, buffer) in ringBuffers) {
            val window = buffer.snapshotWindow(durationMs, nowMs)
            if (window.isNotEmpty()) {
                result[pid] = window
            }
        }
        return result
    }

    /**
     * Clears all buffers (e.g. on vehicle disconnect or new session).
     */
    fun clear() {
        ringBuffers.values.forEach { it.clear() }
        parameterNames.clear()
        parameterUnits.clear()
        lastRecordedMonotonicMs.clear()
    }
}
