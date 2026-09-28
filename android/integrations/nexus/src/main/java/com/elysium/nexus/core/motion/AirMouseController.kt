package com.elysium.nexus.core.motion

import com.elysium.nexus.core.model.MotionState
import kotlin.math.PI
import kotlin.math.abs

/**
 * Elysium Nexus — Air Mouse controller.
 *
 * "Air mouse" is the §14 feature that turns the
 * phone's IMU into a free-space pointer. The user
 * holds the phone in the air like a remote and
 * tilts / rotates it to move the host's cursor.
 *
 * ## The math
 *
 * The class consumes a [MotionState] per IMU
 * sample. For each sample it:
 *
 *  1. Computes the elapsed time `dt` since the
 *     last sample (using the sample's nanosecond
 *     timestamp, not the wall clock).
 *  2. Integrates the gyro `pitch` and `yaw` to
 *     produce a relative rotation. We ignore
 *     `roll` (the phone twisting around its
 *     long axis) because it does not move the
 *     pointer.
 *  3. Maps the rotation to a screen-relative
 *     pointer delta:
 *
 *     - `pitch` (the phone tilting forward /
 *       back) drives the **Y** axis (up / down)
 *     - `yaw` (the phone rotating left / right)
 *       drives the **X** axis (left / right)
 *
 *     The sign is inverted on the Y axis so a
 *     "tilt up" motion moves the cursor up, like
 *     a Wii remote.
 *
 *  4. Multiplies by the user-tunable
 *     [sensitivity] (default 600 px / radian).
 *     At 1 radian (~57°) the cursor moves 600
 *     pixels — a full HD screen takes ~3
 *     radians to traverse.
 *
 *  5. Clamps the per-frame delta to ±127 to fit
 *     in a single HID mouse report byte (the
 *     signed 8-bit range). Frames larger than
 *     127 are split across multiple reports on
 *     consecutive calls to [consume].
 *
 *  6. Drains the integrated rotation by the
 *     amount actually emitted so the next
 *     sample starts from the un-emitted
 *     remainder. This avoids drift when the
 *     cursor hits the 127-byte cap.
 *
 * The class is **pure data + math**; it has no
 * Android dependencies. Unit tests on the JVM
 * exercise the math with synthetic gyro
 * sequences.
 */
class AirMouseController(
    /** Pixels per radian of rotation. Default 600. */
    var sensitivity: Float = 600f,
    /**
     * Invert the Y axis. Default `true` (tilt-up
     * moves the cursor up).
     */
    var invertY: Boolean = true,
    /**
     * The maximum delta per emitted report, in
     * pixels. HID mouse reports use a signed
     * 8-bit byte, so the cap is 127. The
     * controller splits larger rotations into
     * multiple reports.
     */
    var maxDeltaPerReport: Int = 127,
    /**
     * Threshold below which the rotation is
     * ignored. The IMU has noise that produces
     * tiny rotations even when the phone is
     * "still"; this dead-zone prevents the
     * cursor from drifting on its own.
     */
    var deadZoneRadians: Float = 0.003f // ~0.17°
) {
    /**
     * The accumulated pitch / yaw since the last
     * emit. We accumulate, clamp, and drain.
     */
    private var pendingPitch: Float = 0f
    private var pendingYaw: Float = 0f
    private var lastSampleNs: ULong = 0UL

    /**
     * Reset the controller (call when the user
     * lifts / picks up the phone, or after a
     * "recenter" gesture).
     */
    fun reset() {
        pendingPitch = 0f
        pendingYaw = 0f
        lastSampleNs = 0UL
    }

    /**
     * Feed a new [MotionState] sample. The
     * rotation since the last call is integrated
     * into the pending delta.
     */
    fun submit(state: MotionState) {
        val nowNs = state.sampleTimestampNs
        if (lastSampleNs != 0UL) {
            val deltaNs = (nowNs - lastSampleNs).toLong()
            val dtSec = deltaNs / 1e9f
            if (dtSec > 0f && dtSec < 1f) {
                // Sanity: ignore large gaps (sensor
                // pause / app backgrounded).
                // pitch delta = gyroY * dt (rotation
                // around the Y axis tilts the phone
                // forward / back).
                // yaw delta = gyroZ * dt (rotation
                // around the Z axis rotates the
                // phone left / right).
                pendingPitch += state.gyroY * dtSec
                pendingYaw += state.gyroZ * dtSec
            }
        }
        lastSampleNs = nowNs
    }

    /**
     * Drain up to `maxDelta` pixels from the
     * pending rotation. Returns a [Delta] of
     * `dx` / `dy` pixel offsets to send to the
     * host.
     *
     * Call this every frame (e.g. 60 Hz) — the
     * math is cheap and the rotation naturally
     * "catches up" if the consumer is slow.
     */
    fun consume(maxDelta: Int = maxDeltaPerReport): Delta {
        if (abs(pendingPitch) < deadZoneRadians && abs(pendingYaw) < deadZoneRadians) {
            return Delta(0, 0)
        }
        // yaw → X, pitch → Y (with optional invert).
        val rawDx = (pendingYaw * sensitivity)
        val rawDy = (pendingPitch * sensitivity) * if (invertY) -1f else 1f
        // Clamp to ±maxDelta.
        val dx = rawDx.coerceIn(-maxDelta.toFloat(), maxDelta.toFloat()).toInt()
        val dy = rawDy.coerceIn(-maxDelta.toFloat(), maxDelta.toFloat()).toInt()
        // Drain the rotation that produced the
        // emitted delta. We do this in *rotation*
        // units, not pixels, so the math stays
        // correct even if the sensitivity changes
        // mid-flight.
        if (dx != 0) {
            pendingYaw -= dx / sensitivity
        }
        if (dy != 0) {
            pendingPitch -= (dy * if (invertY) -1f else 1f) / sensitivity
        }
        return Delta(dx, dy)
    }

    /**
     * Read-only view of the pending rotation. Used
     * by the unit tests + the UI's "magnitude"
     * indicator (the ring around the cursor grows
     * as the user tilts the phone harder).
     */
    fun pendingMagnitudeRadians(): Float {
        return kotlin.math.sqrt(pendingPitch * pendingPitch + pendingYaw * pendingYaw)
    }

    /**
     * A pixel delta to send to the host. The X
     * axis is the phone's yaw (left / right); the
     * Y axis is the phone's pitch (up / down).
     */
    data class Delta(val dx: Int, val dy: Int)

    companion object {
        /**
         * Suggest a sensitivity for a given
         * screen diagonal. We use a rule of
         * thumb: half the screen diagonal in
         * pixels per radian, so a 1 radian tilt
         * traverses half the screen.
         */
        fun suggestedSensitivity(diagonalPx: Float): Float {
            return diagonalPx / 2f
        }

        /**
         * Convert "radians per second" (gyro
         * output) to "pixels per frame at 60 Hz".
         * Convenience for tests.
         */
        fun radPerSecToPxPerFrame(radPerSec: Float, sensitivity: Float, hz: Float = 60f): Float {
            return radPerSec * sensitivity / hz
        }
    }
}

/**
 * Approximate conversions for the unit tests.
 * Not part of the production API.
 */
internal fun degToRad(deg: Float): Float = deg * (PI.toFloat() / 180f)
internal fun radToDeg(rad: Float): Float = rad * (180f / PI.toFloat())
