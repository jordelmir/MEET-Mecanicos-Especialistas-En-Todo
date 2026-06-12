package com.elysium369.meet.core.obd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * PhoneSpeedTracker — World-class high-refresh-rate (60 FPS) sensor fusion speed tracking.
 * 
 * Merges:
 * 1. Reference Speed Ground Truth: OBD-II speed (VSS PID "010D") or Phone GPS speed.
 * 2. Instant Motion Predictor: Phone's Linear Accelerometer (Sensor.TYPE_LINEAR_ACCELERATION).
 * 
 * Computes estimated vehicle velocity at 60Hz using a Complementary/Kalman filtering approach,
 * completely eliminating low-frequency stepping and delay to achieve Waze-like fluid animations.
 */
@Singleton
class PhoneSpeedTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationListener, SensorEventListener {

    private val tag = "PhoneSpeedTracker"

    // State flow exposing current estimated speed in km/h
    private val _fusedSpeed = MutableStateFlow(0f)
    val fusedSpeed: StateFlow<Float> = _fusedSpeed.asStateFlow()

    // State flows for G-Force & Inclinometer metrics (60Hz visual sweeps)
    private val _lateralG = MutableStateFlow(0f)
    val lateralG: StateFlow<Float> = _lateralG.asStateFlow()

    private val _longitudinalG = MutableStateFlow(0f)
    val longitudinalG: StateFlow<Float> = _longitudinalG.asStateFlow()

    private val _pitch = MutableStateFlow(0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    // Calibration Offsets (allows zeroing the inclinometer/G-force relative to mount angle)
    @Volatile private var pitchOffset = 0f
    @Volatile private var rollOffset = 0f
    @Volatile private var latGOffset = 0f
    @Volatile private var longGOffset = 0f

    // Live Uncalibrated Values (used as reference during calibration)
    @Volatile private var rawPitch = 0f
    @Volatile private var rawRoll = 0f
    @Volatile private var rawLatG = 0f
    @Volatile private var rawLongG = 0f

    // Gravity vector accumulator (fixes high-pass filter scope bug)
    @Volatile private var gravityX = 0f
    @Volatile private var gravityY = 0f
    @Volatile private var gravityZ = 9.8f // Initialize to standard gravity

    // Managers
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Core Filtering State Variables (Thread-Safe or synchronized)
    @Volatile
    private var currentEstimateKmh = 0f
    @Volatile
    private var targetSpeedKmh = 0f
    @Volatile
    private var lastTargetUpdateTime = 0L
    @Volatile
    private var visualSpeedKmh = 0f


    // Accelerometer variables
    @Volatile
    private var accelX = 0f
    @Volatile
    private var accelY = 0f
    @Volatile
    private var accelZ = 0f
    @Volatile
    private var accelFilteredMag = 0f

    // Control flags
    private var isTracking = false
    private var trackerScope: CoroutineScope? = null
    private var updateJob: Job? = null

    // Configuration constants
    private val loopIntervalMs = 8L // ~120 Hz (8.33ms)
    private val gpsMinTimeMs = 1000L // Request GPS updates every 1 second
    private val gpsMinDistanceM = 0f

    // Dead-reckoning / Filter settings
    private val alphaNormal = 0.07f   // Default smoothing factor (lower = smoother)
    private val alphaActive = 0.18f   // Responsive smoothing factor when accelerating/braking
    private val accelDeadband = 0.25f // Filter out engine vibrations and small bumps (m/s^2)
    private val maxAccelLimit = 6.5f  // Clamp impossible acceleration spikes (m/s^2)

    /**
     * Start the sensor fusion and GPS tracking services.
     */
    @Synchronized
    fun start() {
        if (isTracking) return
        isTracking = true
        Log.d(tag, "🚀 Starting PhoneSpeedTracker...")

        // 1. Reset state
        currentEstimateKmh = 0f
        targetSpeedKmh = 0f
        visualSpeedKmh = 0f
        accelFilteredMag = 0f
        _fusedSpeed.value = 0f

        // 2. Register Sensors
        val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccel != null) {
            sensorManager.registerListener(this, linearAccel, SensorManager.SENSOR_DELAY_GAME)
            Log.d(tag, "✅ Linear Accelerometer registered successfully.")
        }
        
        val standardAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (standardAccel != null) {
            sensorManager.registerListener(this, standardAccel, SensorManager.SENSOR_DELAY_GAME)
            Log.d(tag, "✅ Standard Accelerometer registered successfully.")
        } else {
            Log.e(tag, "❌ No standard accelerometer detected on this device.")
        }

        // 3. Register GPS Location Updates if permissions are granted
        requestGpsUpdates()

        // 4. Start 60Hz estimation loop
        trackerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        updateJob = trackerScope?.launch {
            val dt = loopIntervalMs / 1000f // Time step in seconds
            while (isActive) {
                fuseAndCalculate(dt)
                delay(loopIntervalMs)
            }
        }
    }

    /**
     * Stop tracking sensors and GPS to conserve device battery.
     */
    @Synchronized
    fun stop() {
        if (!isTracking) return
        isTracking = false
        Log.d(tag, "🛑 Stopping PhoneSpeedTracker...")

        // Unregister listeners
        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException while removing location updates: ${e.message}")
        }

        // Cancel Coroutine job
        updateJob?.cancel()
        updateJob = null
        trackerScope = null
    }

    /**
     * Feed raw speed measurements from OBD-II (VSS PID "010D").
     * When OBD is active, this overrides GPS as the primary speed reference.
     */
    fun setObdSpeed(speedKmh: Float) {
        targetSpeedKmh = speedKmh
        lastTargetUpdateTime = System.currentTimeMillis()
    }

    /**
     * Safety check and registration of GPS updates.
     */
    private fun requestGpsUpdates() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            try {
                // Request updates from GPS Provider (highest precision)
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        gpsMinTimeMs,
                        gpsMinDistanceM,
                        this
                    )
                    Log.d(tag, "🛰️ GPS Provider registered for location updates.")
                }
                
                // Fallback / Supplementary Network Provider (urban canyons / indoors)
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        gpsMinTimeMs,
                        gpsMinDistanceM,
                        this
                    )
                    Log.d(tag, "📡 Network Provider registered for location updates.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to request location updates: ${e.message}", e)
            }
        } else {
            Log.w(tag, "🔐 Location permission not granted. Running in OBD-only/sensor-only speed mode.")
        }
    }

    /**
     * Sensor Fusion Filter core mathematics (60 FPS Loop).
     */
    private fun fuseAndCalculate(dt: Float) {
        // 1. Calculate raw magnitude of acceleration in m/s^2
        val rawMag = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)

        // 2. Filter out gravity/noise using a deadband & low pass filter
        val baseAcceleration = if (rawMag > accelDeadband) rawMag else 0f
        
        // Low pass filter to eliminate engine vibration frequency
        val beta = 0.15f
        accelFilteredMag = (1f - beta) * accelFilteredMag + beta * baseAcceleration
        
        // Clamp to physically possible passenger vehicle dynamics (6.5 m/s^2 corresponds to 0-100kmh in ~4.2s)
        val accelClamped = accelFilteredMag.coerceAtMost(maxAccelLimit)

        // 3. Determine sign of acceleration (accelerating vs braking) based on difference with target
        val speedDifference = targetSpeedKmh - currentEstimateKmh
        val accelSign = when {
            speedDifference > 0.4f -> 1.0f  // Accelerating
            speedDifference < -0.4f -> -1.0f // Decelerating / Braking
            else -> 0f                      // Steady state / cruising
        }

        // 4. PREDICTION STEP (Dead-Reckoning):
        // Translate acceleration (m/s^2) into velocity delta (km/h) for the current dt step
        // Delta V = acceleration * dt (seconds) * 3.6 (m/s -> km/h conversion)
        val accelContribution = accelSign * accelClamped * dt * 3.6f
        currentEstimateKmh += accelContribution

        // 5. CORRECTION/SMOOTHING STEP:
        // Use a dynamic/adaptive interpolation coefficient. If we are actively accelerating
        // or braking (high speedDifference or high acceleration), we trust the target more (higher alpha)
        // to reduce lag. If cruising or stopped, we apply heavy smoothing (lower alpha) to eliminate noise.
        val isActivelyChanging = accelSign != 0f || accelClamped > 1.0f
        val currentAlpha = if (isActivelyChanging) alphaActive else alphaNormal
        
        currentEstimateKmh += currentAlpha * (targetSpeedKmh - currentEstimateKmh)

        // 6. Post-processing constraints
        currentEstimateKmh = currentEstimateKmh.coerceAtLeast(0f)
        
        // If target speed is 0 and estimate is very small (< 1km/h), snap to 0 immediately to prevent slow creep
        if (targetSpeedKmh == 0f && currentEstimateKmh < 1.0f) {
            currentEstimateKmh = 0f
        }

        // 7. Apply visual step-rate limiting to ensure speed sweeps through every single integer value
        // Limit the maximum change per frame at 60Hz to ~0.95 km/h.
        // This guarantees that the speedometer increments/decrements by at most 1 unit per frame,
        // passing through every single integer value visually (similar to Waze).
        if (visualSpeedKmh == 0f && currentEstimateKmh > 0f && targetSpeedKmh > 5f) {
            // Snap on initial start moving to prevent artificial lag at 0 km/h
            visualSpeedKmh = currentEstimateKmh
        } else {
            val visualDiff = currentEstimateKmh - visualSpeedKmh
            val maxVisualStep = 0.95f // Max speed change in km/h per frame (~57 km/h per second)
            val visualStep = visualDiff.coerceIn(-maxVisualStep, maxVisualStep)
            visualSpeedKmh += visualStep
        }

        visualSpeedKmh = visualSpeedKmh.coerceAtLeast(0f)
        if (currentEstimateKmh == 0f && visualSpeedKmh < 0.5f) {
            visualSpeedKmh = 0f
        }

        // 8. Publish the visual speed update
        _fusedSpeed.value = visualSpeedKmh
    }

    // --- LocationListener Implementation ---
    override fun onLocationChanged(location: Location) {
        // If OBD speed hasn't updated in the last 2.5 seconds, treat GPS as primary source
        val timeSinceLastObd = System.currentTimeMillis() - lastTargetUpdateTime
        if (timeSinceLastObd > 2500) {
            if (location.hasSpeed()) {
                // Location.getSpeed() returns meters/second -> convert to km/h
                targetSpeedKmh = location.speed * 3.6f
            }
        }
    }

    @Deprecated("Deprecated in Android API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // --- SensorEventListener Implementation ---
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            accelX = event.values[0]
            accelY = event.values[1]
            accelZ = event.values[2]

            // Convert linear acceleration (m/s^2) to G-Force (1G = 9.81 m/s^2)
            rawLatG = accelX / 9.81f
            rawLongG = accelY / 9.81f

            // Publish G-force with calibration offsets subtracted
            _lateralG.value = rawLatG - latGOffset
            _longitudinalG.value = rawLongG - longGOffset
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            // 1. Maintain gravity vector for high-pass fallback filter (speed tracking)
            val alphaG = 0.8f
            gravityX = alphaG * gravityX + (1f - alphaG) * ax
            gravityY = alphaG * gravityY + (1f - alphaG) * ay
            gravityZ = alphaG * gravityZ + (1f - alphaG) * az

            // If linear accelerometer hardware is absent, estimate it from standard accelerometer
            val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            if (linearAccel == null) {
                accelX = ax - gravityX
                accelY = ay - gravityY
                accelZ = az - gravityZ

                rawLatG = accelX / 9.81f
                rawLongG = accelY / 9.81f
                _lateralG.value = rawLatG - latGOffset
                _longitudinalG.value = rawLongG - longGOffset
            }

            // 2. Calculate Pitch & Roll from gravity acceleration vector
            // Pitch: tilting forward/backward (-90 to 90 deg)
            val computedPitch = Math.toDegrees(atan2(-ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
            // Roll: tilting left/right (-180 to 180 deg)
            val computedRoll = Math.toDegrees(atan2(ay.toDouble(), az.toDouble())).toFloat()

            // Smooth angles with a gentle low-pass filter to eliminate engine vibration jitter
            val smoothing = 0.15f
            rawPitch = (1f - smoothing) * rawPitch + smoothing * computedPitch
            rawRoll = (1f - smoothing) * rawRoll + smoothing * computedRoll

            // Publish Tilt/Inclinometer angles with calibration offsets
            _pitch.value = rawPitch - pitchOffset
            _roll.value = rawRoll - rollOffset
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Calibrate/zero sensors: sets the current raw G-Force and Pitch/Roll angles as 0 references.
     * This is useful to compensate for the angle of dashboard phone holders.
     */
    fun calibrateSensors() {
        pitchOffset = rawPitch
        rollOffset = rawRoll
        latGOffset = rawLatG
        longGOffset = rawLongG
        Log.d(tag, "📌 Sensors calibrated: pitchOffset=$pitchOffset, rollOffset=$rollOffset, latGOffset=$latGOffset, longGOffset=$longGOffset")
    }

    /**
     * Resets calibration offsets back to factory baseline (uncalibrated).
     */
    fun resetCalibration() {
        pitchOffset = 0f
        rollOffset = 0f
        latGOffset = 0f
        longGOffset = 0f
        Log.d(tag, "🔄 Sensor calibration reset to factory defaults.")
    }
}
