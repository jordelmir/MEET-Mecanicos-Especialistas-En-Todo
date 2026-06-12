package com.elysium369.meet.core.obd

import android.util.Log

/**
 * PredictiveFilter - A high-fidelity real-time physics-based state estimator (Kalman-like prediction filter).
 *
 * Between OBD-II readings (which arrive at low frequencies of 2-10Hz), this filter
 * estimates the live values of fast-changing parameters (RPM, Speed, Throttle)
 * at 120Hz.
 *
 * It models the parameter as a moving state with velocity:
 *   Value_est = Value_last + Velocity * dt
 *
 * And corrects the velocity and estimate dynamically upon receiving new OBD-II values,
 * applying a low-pass filter to the velocity to filter out communication noise spikes.
 */
class PredictiveFilter(
    private val pid: String,
    private val maxVelocity: Float,        // Max change per millisecond (prevents noise spikes)
    private val correctionAlpha: Float = 0.18f // Speed of convergence to raw value per 120Hz tick
) {
    private val tag = "PredictiveFilter-$pid"
    private var currentEstimate: Float = 0f
    private var lastRawValue: Float = 0f
    private var lastRawTime: Long = 0L
    private var velocity: Float = 0f // rate of change per millisecond
    private var initialized = false

    /**
     * Feed a new raw OBD-II value.
     */
    @Synchronized
    fun onNewRawValue(rawValue: Float) {
        val now = System.currentTimeMillis()
        if (!initialized) {
            currentEstimate = rawValue
            lastRawValue = rawValue
            lastRawTime = now
            velocity = 0f
            initialized = true
            return
        }

        val dt = (now - lastRawTime).coerceAtLeast(1L)
        
        // Calculate raw velocity (delta value / delta time in ms)
        val rawVelocity = (rawValue - lastRawValue) / dt.toFloat()
        
        // Low pass filter on velocity to smooth out sudden OBD noise/stepping
        val alphaV = 0.35f
        velocity = (1f - alphaV) * velocity + alphaV * rawVelocity
        
        // Clamp velocity to physically possible limits
        velocity = velocity.coerceIn(-maxVelocity, maxVelocity)

        lastRawValue = rawValue
        lastRawTime = now
    }

    /**
     * Extrapolate/predict the value for the elapsed time step.
     * Call this in the 120Hz loop.
     *
     * @param dtMs Delta time in milliseconds since the last prediction tick.
     */
    @Synchronized
    fun predict(dtMs: Float): Float {
        if (!initialized) return 0f

        val now = System.currentTimeMillis()
        val isStale = (now - lastRawTime) > 500L

        if (isStale) {
            // Decay velocity to 0 when raw OBD values are stale
            velocity *= 0.85f
            if (Math.abs(velocity) < 0.001f) {
                velocity = 0f
            }
        }

        // 1. Prediction step: Extrapolate using velocity
        currentEstimate += velocity * dtMs

        // 2. Correction step: Attract estimate toward the latest raw OBD reference
        // to prevent drift. If data is stale, increase convergence speed.
        val alpha = if (isStale) 0.5f else correctionAlpha
        currentEstimate += alpha * (lastRawValue - currentEstimate)

        // 3. Prevent negative values for absolute PIDs (RPM, Speed, Throttle, etc. are positive)
        currentEstimate = currentEstimate.coerceAtLeast(0f)

        return currentEstimate
    }

    @Synchronized
    fun reset() {
        currentEstimate = 0f
        lastRawValue = 0f
        lastRawTime = 0L
        velocity = 0f
        initialized = false
    }
}

/**
 * Manages predictive state estimation for multiple fast OBD PIDs.
 */
class PredictiveTelemetryEstimator {
    private val filters = mutableMapOf<String, PredictiveFilter>()

    init {
        // Initialize filters for fast-changing PIDs with physical velocity limits
        // limits are specified in: units per millisecond
        
        // 010C (RPM): Max acceleration of 6000 RPM/sec -> 6.0 RPM/ms
        filters["010C"] = PredictiveFilter(pid = "010C", maxVelocity = 6.0f, correctionAlpha = 0.16f)
        filters["0C"] = PredictiveFilter(pid = "0C", maxVelocity = 6.0f, correctionAlpha = 0.16f)
        
        // 010D (Speed): Max acceleration/deceleration of 45 km/h per sec -> 0.045 km/h / ms
        filters["010D"] = PredictiveFilter(pid = "010D", maxVelocity = 0.045f, correctionAlpha = 0.18f)
        filters["0D"] = PredictiveFilter(pid = "0D", maxVelocity = 0.045f, correctionAlpha = 0.18f)
        
        // 0111 (Throttle Position): Full stroke in 150ms -> 100% / 150ms = 0.67 % / ms
        filters["0111"] = PredictiveFilter(pid = "0111", maxVelocity = 0.67f, correctionAlpha = 0.20f)
        filters["11"] = PredictiveFilter(pid = "11", maxVelocity = 0.67f, correctionAlpha = 0.20f)
        
        // 0104 (Engine Load): Max change of 100% in 200ms -> 0.50 % / ms
        filters["0104"] = PredictiveFilter(pid = "0104", maxVelocity = 0.50f, correctionAlpha = 0.18f)
        filters["04"] = PredictiveFilter(pid = "04", maxVelocity = 0.50f, correctionAlpha = 0.18f)
        
        // 0110 (MAF Air Flow Rate): Max change of 150 g/s in 300ms -> 0.50 g/s / ms
        filters["0110"] = PredictiveFilter(pid = "0110", maxVelocity = 0.50f, correctionAlpha = 0.16f)
        filters["10"] = PredictiveFilter(pid = "10", maxVelocity = 0.50f, correctionAlpha = 0.16f)
    }

    /**
     * Feed new raw/smoothed OBD measurements to update the predictive models.
     */
    @Synchronized
    fun updateRawValues(data: Map<String, Float>) {
        data.forEach { (pid, value) ->
            filters[pid]?.onNewRawValue(value)
        }
    }

    /**
     * Predict values at 120Hz. Returns a map with estimated values merged
     * into the existing live data map.
     */
    @Synchronized
    fun getEstimatedMap(baseMap: Map<String, Float>, dtMs: Float): Map<String, Float> {
        val mutable = baseMap.toMutableMap()
        filters.forEach { (pid, filter) ->
            if (baseMap.containsKey(pid)) {
                mutable[pid] = filter.predict(dtMs)
            }
        }
        return mutable
    }

    @Synchronized
    fun reset() {
        filters.values.forEach { it.reset() }
    }
}
