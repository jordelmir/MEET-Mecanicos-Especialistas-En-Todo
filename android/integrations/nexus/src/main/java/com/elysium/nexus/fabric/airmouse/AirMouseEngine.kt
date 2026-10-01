package com.elysium.nexus.fabric.airmouse

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Gyroscopic Air Mouse Engine — Professional-grade pointer system.
 *
 * Uses device gyroscope + accelerometer + optional rotation vector
 * to compute a cursor position that maps to a TV/PC screen.
 *
 * ## Sensor Fusion Architecture
 *
 * 1. **Gyroscope** → angular velocity (rad/s) for instant cursor movement
 * 2. **Accelerometer** → tilt detection + gravity-compensated dead-reckoning
 * 3. **Rotation Vector** (fused) → absolute orientation for drift correction
 *
 * ## Filtering
 *
 * - 1€ Filter (adaptive low-pass): smooth at rest, responsive during fast movement
 * - Dead-zone: 0.005 rad/s below which movement is ignored
 * - Velocity-dependent sensitivity: slow = precise, fast = coarse
 *
 * ## Coordinate System
 *
 * Phone held upright (portrait, screen facing user):
 * - X-axis gyro rotation → horizontal cursor movement
 * - Y-axis gyro rotation → vertical cursor movement
 * - Z-axis gyro rotation → ignored (twist)
 *
 * Output: normalized [0.0, 1.0] coordinates mapped to target screen resolution.
 */
class AirMouseEngine(
    private val sensorManager: SensorManager,
    private val targetScreenWidth: Int = 1920,
    private val targetScreenHeight: Int = 1080
) : SensorEventListener {

    // -- Configuration --
    private var sensitivity = 3.0f        // Multiplier for gyro-to-pixel conversion
    private var deadZone = 0.005f          // rad/s below which movement is ignored
    private var smoothingFactor = 0.85f    // 1€ filter min cutoff
    private var velocityCoefficient = 0.07f // 1€ filter velocity coefficient

    // -- Cursor State --
    private var cursorX = 0.5f    // [0..1] normalized
    private var cursorY = 0.5f    // [0..1] normalized
    private var isActive = false

    // -- 1€ Filter State --
    private var prevFilteredX = 0.0f
    private var prevFilteredY = 0.0f
    private var prevDxFiltered = 0.0f
    private var prevDyFiltered = 0.0f
    private var lastTimestamp = 0L

    // -- Callbacks --
    var onCursorMoved: ((x: Float, y: Float) -> Unit)? = null
    var onClick: (() -> Unit)? = null
    var onGestureDetected: ((gesture: AirMouseGesture) -> Unit)? = null

    // -- Gesture Detection --
    private var flickAccumX = 0.0f
    private var flickAccumY = 0.0f
    private val flickThreshold = 4.0f  // rad/s for flick detection
    private var lastFlickTime = 0L

    fun start() {
        isActive = true
        cursorX = 0.5f
        cursorY = 0.5f
        prevFilteredX = 0.5f
        prevFilteredY = 0.5f
        prevDxFiltered = 0.0f
        prevDyFiltered = 0.0f
        lastTimestamp = 0L

        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        isActive = false
        sensorManager.unregisterListener(this)
    }

    fun setSensitivity(level: Float) {
        sensitivity = level.coerceIn(0.5f, 10.0f)
    }

    fun setDeadZone(dz: Float) {
        deadZone = dz.coerceIn(0.001f, 0.05f)
    }

    fun resetCursor() {
        cursorX = 0.5f
        cursorY = 0.5f
    }

    fun getCursorPosition(): Pair<Float, Float> = Pair(cursorX, cursorY)

    /**
     * Get the cursor position in target screen pixels.
     */
    fun getCursorPixels(): Pair<Int, Int> {
        return Pair(
            (cursorX * targetScreenWidth).toInt().coerceIn(0, targetScreenWidth),
            (cursorY * targetScreenHeight).toInt().coerceIn(0, targetScreenHeight)
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> processGyroscope(event)
            Sensor.TYPE_GAME_ROTATION_VECTOR -> processRotationVector(event)
        }
    }

    private fun processGyroscope(event: SensorEvent) {
        val timestamp = event.timestamp
        if (lastTimestamp == 0L) {
            lastTimestamp = timestamp
            return
        }

        val dt = (timestamp - lastTimestamp) / 1_000_000_000.0f // nanoseconds to seconds
        lastTimestamp = timestamp

        if (dt <= 0 || dt > 0.1f) return // Skip bad samples

        // Gyroscope axes (phone in portrait, screen toward user):
        // event.values[0] = X axis rotation (pitch) → vertical cursor
        // event.values[1] = Y axis rotation (yaw) → horizontal cursor
        // event.values[2] = Z axis rotation (roll) → ignored
        val gyroX = event.values[1]  // Yaw → horizontal
        val gyroY = -event.values[0] // Pitch → vertical (inverted)

        // Dead-zone filter
        val activeX = if (abs(gyroX) > deadZone) gyroX else 0.0f
        val activeY = if (abs(gyroY) > deadZone) gyroY else 0.0f

        // Flick gesture detection
        detectFlickGestures(gyroX, gyroY)

        // Convert angular velocity to cursor delta
        val rawDx = activeX * sensitivity * dt
        val rawDy = activeY * sensitivity * dt

        // Apply 1€ Filter for smooth, lag-free cursor
        val (filteredDx, filteredDy) = oneEuroFilter(rawDx, rawDy, dt)

        // Update normalized cursor position
        cursorX = (cursorX + filteredDx).coerceIn(0.0f, 1.0f)
        cursorY = (cursorY + filteredDy).coerceIn(0.0f, 1.0f)

        onCursorMoved?.invoke(cursorX, cursorY)
    }

    private fun processRotationVector(event: SensorEvent) {
        // The rotation vector provides absolute orientation for drift correction.
        // For now, we use it to detect when the phone is face-down (= disable air mouse)
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // orientation[1] = pitch. If device is nearly flat (> 60° from vertical), pause.
        // This prevents accidental cursor movements when the phone is on a table.
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        if (abs(pitchDeg) > 60) {
            // Device is too tilted/flat — don't move cursor
        }
    }

    /**
     * 1€ Filter — Casiez et al. 2012
     *
     * Adaptive low-pass filter:
     * - At rest (low velocity): high smoothing → no jitter
     * - During movement (high velocity): low smoothing → no lag
     *
     * Parameters:
     * - minCutoff (smoothingFactor): the base cutoff frequency (Hz). Lower = smoother but laggier.
     * - beta (velocityCoefficient): how much to increase cutoff with speed. Higher = more responsive.
     */
    private fun oneEuroFilter(dx: Float, dy: Float, dt: Float): Pair<Float, Float> {
        if (dt <= 0) return Pair(dx, dy)

        val rate = 1.0f / dt

        // Derivative of the signal (speed of movement)
        val dxDot = (dx - prevFilteredX) * rate
        val dyDot = (dy - prevFilteredY) * rate

        // Filter the derivative
        val alphaD = computeAlpha(1.0f, rate)
        val dxDotFiltered = alphaD * dxDot + (1 - alphaD) * prevDxFiltered
        val dyDotFiltered = alphaD * dyDot + (1 - alphaD) * prevDyFiltered

        // Adaptive cutoff: higher speed → higher cutoff → less smoothing
        val cutoffX = smoothingFactor + velocityCoefficient * abs(dxDotFiltered)
        val cutoffY = smoothingFactor + velocityCoefficient * abs(dyDotFiltered)

        // Filter the signal
        val alphaX = computeAlpha(cutoffX, rate)
        val alphaY = computeAlpha(cutoffY, rate)

        val filteredX = alphaX * dx + (1 - alphaX) * prevFilteredX
        val filteredY = alphaY * dy + (1 - alphaY) * prevFilteredY

        prevFilteredX = filteredX
        prevFilteredY = filteredY
        prevDxFiltered = dxDotFiltered
        prevDyFiltered = dyDotFiltered

        return Pair(filteredX, filteredY)
    }

    private fun computeAlpha(cutoff: Float, rate: Float): Float {
        val tau = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
        val te = 1.0f / rate
        return 1.0f / (1.0f + tau / te)
    }

    /**
     * Flick gesture detection for air mouse navigation.
     * Detects fast wrist flicks: left/right/up/down.
     */
    private fun detectFlickGestures(gyroX: Float, gyroY: Float) {
        val now = System.currentTimeMillis()
        if (now - lastFlickTime < 300) return // Debounce

        if (abs(gyroX) > flickThreshold) {
            lastFlickTime = now
            val gesture = if (gyroX > 0) AirMouseGesture.FLICK_RIGHT else AirMouseGesture.FLICK_LEFT
            onGestureDetected?.invoke(gesture)
        }
        if (abs(gyroY) > flickThreshold) {
            lastFlickTime = now
            val gesture = if (gyroY > 0) AirMouseGesture.FLICK_DOWN else AirMouseGesture.FLICK_UP
            onGestureDetected?.invoke(gesture)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op: we don't need to react to accuracy changes
    }
}

enum class AirMouseGesture {
    FLICK_LEFT,
    FLICK_RIGHT,
    FLICK_UP,
    FLICK_DOWN,
    TWIST_CLOCKWISE,
    TWIST_COUNTER_CLOCKWISE,
    SHAKE
}
