package com.elysium369.meet.core.obd

import java.util.ArrayDeque

/**
 * Production-grade sensor value smoother using a moving average filter
 * combined with exponential interpolation. Eliminates erratic jumps from
 * raw ELM327 readings while maintaining responsiveness.
 *
 * Architecture:
 * 1. Raw value enters → moving average filter (reduces noise)
 * 2. Filtered value → exponential interpolation toward target (smooth transitions)
 * 3. Display value emitted via callback
 *
 * @param windowSize Number of readings for the moving average window.
 *                   Larger = smoother but more latent. Default 5 is ideal for OBD2.
 * @param smoothingFactor Exponential smoothing alpha (0.0 = instant snap, 1.0 = max lag).
 *                        0.15 provides professional-grade smooth transitions.
 */
class SmoothSensorValue(
    private val windowSize: Int = 5,
    private val smoothingFactor: Float = 0.15f
) {
    private var currentDisplayValue: Float = 0f
    private var targetValue: Float = 0f
    private val history = ArrayDeque<Float>(windowSize + 1)
    private var initialized = false

    /**
     * Feed a new raw sensor reading. Returns the smoothed display value.
     *
     * The smoothing pipeline:
     * 1. Add to moving average window
     * 2. Compute windowed average (noise filter)
     * 3. Exponentially interpolate from current display value toward average
     */
    fun update(rawValue: Float): Float {
        // Moving average filter — removes ELM327 noise spikes
        history.addLast(rawValue)
        if (history.size > windowSize) history.removeFirst()

        val averagedTarget = history.average().toFloat()

        if (!initialized) {
            // First reading — snap to value immediately
            currentDisplayValue = averagedTarget
            targetValue = averagedTarget
            initialized = true
            return currentDisplayValue
        }

        targetValue = averagedTarget

        // Exponential moving average interpolation
        // display = display + alpha * (target - display)
        val alpha = 1f - smoothingFactor
        currentDisplayValue += alpha * (targetValue - currentDisplayValue)

        return currentDisplayValue
    }

    /**
     * Get the current smoothed display value without updating.
     */
    fun currentValue(): Float = currentDisplayValue

    /**
     * Get the raw target value (after moving average, before interpolation).
     */
    fun targetValue(): Float = targetValue

    /**
     * Snap immediately to a value, bypassing all smoothing.
     * Use when switching sensors or resetting state.
     */
    fun snapTo(value: Float) {
        currentDisplayValue = value
        targetValue = value
        history.clear()
        history.addLast(value)
        initialized = true
    }

    /**
     * Reset to uninitialized state. Next update will snap.
     */
    fun reset() {
        currentDisplayValue = 0f
        targetValue = 0f
        history.clear()
        initialized = false
    }
}

/**
 * Manages a set of SmoothSensorValue instances keyed by PID.
 * Thread-safe for use from coroutine collectors.
 *
 * Usage in ViewModel:
 * ```
 * private val sensorSmoother = SensorSmootherManager()
 *
 * // In liveData collector:
 * val smoothedData = sensorSmoother.smoothAll(rawData)
 * _liveData.value = smoothedData
 * ```
 */
class SensorSmootherManager {

    private val smoothers = mutableMapOf<String, SmoothSensorValue>()

    /**
     * Smooth a single PID reading.
     */
    @Synchronized
    fun smooth(pid: String, rawValue: Float): Float {
        val smoother = smoothers.getOrPut(pid) {
            SmoothSensorValue(
                windowSize = getWindowSizeForPid(pid),
                smoothingFactor = getSmoothingFactorForPid(pid)
            )
        }
        return smoother.update(rawValue)
    }

    /**
     * Smooth all PID readings in a batch.
     * Returns a new map with smoothed values.
     */
    @Synchronized
    fun smoothAll(rawData: Map<String, Float>): Map<String, Float> {
        return rawData.mapValues { (pid, value) -> smooth(pid, value) }
    }

    /**
     * Reset a specific PID's smoother (e.g., on disconnect).
     */
    @Synchronized
    fun resetPid(pid: String) {
        smoothers[pid]?.reset()
    }

    /**
     * Reset all smoothers (e.g., on vehicle change).
     */
    @Synchronized
    fun resetAll() {
        smoothers.values.forEach { it.reset() }
        smoothers.clear()
    }

    /**
     * Per-PID tuning: critical/fast-changing sensors get less smoothing
     * for responsiveness; slow sensors get more smoothing for stability.
     */
    private fun getWindowSizeForPid(pid: String): Int {
        return when (pid) {
            // Critical — fast response needed
            "0C", "0D" -> 3  // RPM, Speed — minimal window
            // Fast — moderate response
            "11", "04", "10", "49" -> 4  // Throttle, Load, MAF, Pedal
            // Medium — temperature/voltage
            "05", "42", "5C" -> 6  // Coolant, Battery, Oil temp
            // Slow — stable readings preferred
            "2F", "0A", "46", "33" -> 8  // Fuel level, Fuel pressure, Ambient, Baro
            // Default
            else -> 5
        }
    }

    private fun getSmoothingFactorForPid(pid: String): Float {
        return when (pid) {
            // Critical — nearly instant updates
            "0C", "0D" -> 0.05f  // RPM, Speed
            // Fast — quick but filtered
            "11", "04", "10", "49" -> 0.10f  // Throttle, Load, MAF, Pedal
            // Medium — smooth transitions
            "05", "42", "5C" -> 0.20f  // Coolant, Battery, Oil temp
            // Slow — very stable display
            "2F", "0A", "46", "33", "1F" -> 0.30f  // Fuel, Pressure, Ambient, Runtime
            // Default
            else -> 0.15f
        }
    }
}
