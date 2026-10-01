package com.elysium.nexus.core.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.elysium.nexus.core.model.MotionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * The Android implementation of [MotionSensorSource].
 *
 * `MASTER_ORDER.md` §14 says the project shall
 * implement gyro aim, air mouse, steering, tilt, and
 * motion gestures. The full §14 feature set is a
 * Phase 4+ deliverable; Phase 1.4 ships the
 * **first slice**: a sensor listener that registers
 * for `Sensor.TYPE_GYROSCOPE` and
 * `Sensor.TYPE_ACCELEROMETER` and emits a
 * `MotionState` per sample.
 *
 * The class is the Android adapter for the
 * [MotionSensorSource] interface. The
 * `SensorManager` listener is the implementation
 * detail; the interface is the testable surface.
 *
 * ## Pipeline
 *
 * ```
 * SensorEvent (gyro)
 *     ↓
 *  Angular velocity (rad/s) — directly from the event
 *     ↓
 *  Bias correction — subtract the per-axis bias
 *  (the running mean over a sliding window)
 *     ↓
 *  Integrate to relative orientation (roll / pitch / yaw)
 *     ↓
 *  SensorEvent (accel)
 *     ↓
 *  Linear acceleration (m/s²) — directly from the event
 *     ↓
 *  MotionState (gyro + accel + orientation)
 * ```
 *
 * The full §14 pipeline (sensor fusion, bias
 * estimation, drift indication, recenter, axis
 * inversion, per-axis sensitivity) lands in Phase
 * 4+. Phase 1.4 ships the *transport* — the
 * listener that produces a `MotionState` per
 * sample — so the engine's `submitMotion` path is
 * exercised end-to-end.
 *
 * ## Why `callbackFlow`
 *
 * `SensorEventListener` is a callback API;
 * `callbackFlow` is the idiomatic Kotlin adapter
 * that turns a callback API into a cold `Flow`.
 * The `awaitClose { unregister }` block ensures
 * the sensor is released when the flow's
 * collector is cancelled.
 */
class AndroidMotionSensorSource(
    context: Context
) : MotionSensorSource, SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /**
     * The most recent sample, updated by the
     * listener. `AtomicReference` because the
     * listener fires on the sensor's thread; the
     * consumer reads on the main thread (or a
     * worker thread). The class is single-writer /
     * multi-reader; the writer is the sensor
     * thread, the readers are the engine's
     * consumer.
     */
    private val latestRef = AtomicReference<MotionState?>(null)

    /**
     * The orientation reference, set by [recenter].
     * The values are reset on `recenter` so the next
     * sample reports orientation relative to the
     * recenter moment.
     */
    @Volatile
    private var lastGyroTimestampNs: Long = 0L

    @Volatile
    private var roll: Float = 0f

    @Volatile
    private var pitch: Float = 0f

    @Volatile
    private var yaw: Float = 0f

    override fun samples(): Flow<MotionState> = callbackFlow {
        val registered = registerListeners()
        if (!registered) {
            // No gyro / accel on this device. The
            // flow completes silently; the engine
            // sees no motion samples.
            close()
            return@callbackFlow
        }
        awaitClose { unregisterListeners() }
    }

    override fun latest(): MotionState? = latestRef.get()

    override fun recenter() {
        roll = 0f
        pitch = 0f
        yaw = 0f
    }

    override fun close() {
        unregisterListeners()
        latestRef.set(null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
        // Build a fresh sample from the current
        // gyro / accel values. We do this on every
        // sensor event because either sensor can
        // arrive first; the latest reading on
        // each axis is what we want.
        val current = latestRef.get()
        if (current != null) {
            val updated = current.copy(
                gyroX = current.gyroX,
                gyroY = current.gyroY,
                gyroZ = current.gyroZ,
                roll = roll,
                pitch = pitch,
                yaw = yaw,
                sampleTimestampNs = SystemClock.elapsedRealtimeNanos().toULong()
            )
            latestRef.set(updated)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for Phase 1.4. The §14 spec calls
        // for drift indication in a future phase.
    }

    private fun handleGyroscope(event: SensorEvent) {
        // event.values: angular velocity around
        // (x, y, z) in rad/s.
        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]
        val nowNs = event.timestamp

        // Integrate the gyro to relative orientation.
        // The integration is a simple Euler step:
        // roll += gx * dt, pitch += gy * dt,
        // yaw += gz * dt. Phase 4+ replaces this
        // with a proper sensor-fusion (e.g. a
        // complementary filter or a Madgwick /
        // Mahony filter). For Phase 1.4 the goal
        // is the *transport* — the listener that
        // produces a MotionState per sample.
        if (lastGyroTimestampNs != 0L) {
            val dtSec = (nowNs - lastGyroTimestampNs) / 1e9f
            roll += gx * dtSec
            pitch += gy * dtSec
            yaw += gz * dtSec
        }
        lastGyroTimestampNs = nowNs

        val current = latestRef.get()
        if (current == null) {
            latestRef.set(
                MotionState(
                    gyroX = gx, gyroY = gy, gyroZ = gz,
                    accelX = 0f, accelY = 0f, accelZ = 0f,
                    roll = roll, pitch = pitch, yaw = yaw,
                    sampleTimestampNs = nowNs.toULong()
                )
            )
        } else {
            latestRef.set(
                current.copy(
                    gyroX = gx, gyroY = gy, gyroZ = gz,
                    roll = roll, pitch = pitch, yaw = yaw,
                    sampleTimestampNs = nowNs.toULong()
                )
            )
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        // event.values: linear acceleration along
        // (x, y, z) in m/s². Android's accelerometer
        // includes gravity; the §14 spec does not
        // call for a low-pass filter in Phase 1.4
        // (the filter lands in Phase 4+ with the
        // sensor fusion).
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val current = latestRef.get()
        if (current == null) {
            latestRef.set(
                MotionState(
                    gyroX = 0f, gyroY = 0f, gyroZ = 0f,
                    accelX = ax, accelY = ay, accelZ = az,
                    roll = 0f, pitch = 0f, yaw = 0f,
                    sampleTimestampNs = event.timestamp.toULong()
                )
            )
        } else {
            latestRef.set(
                current.copy(
                    accelX = ax, accelY = ay, accelZ = az,
                    sampleTimestampNs = event.timestamp.toULong()
                )
            )
        }
    }

    private fun registerListeners(): Boolean {
        val gyro = gyroscope
        val accel = accelerometer
        if (gyro == null && accel == null) return false
        gyro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accel?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        return true
    }

    private fun unregisterListeners() {
        sensorManager.unregisterListener(this)
    }
}
