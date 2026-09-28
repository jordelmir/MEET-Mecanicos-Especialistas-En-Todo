package com.elysium.nexus.core.model

/**
 * Canonical motion / IMU state.
 *
 * Per `MASTER_ORDER.md` §14:
 *
 *  - gyroscope in rad/s (no hard cap; consumer backends saturate)
 *  - accelerometer in m/s² (no hard cap; consumer backends saturate)
 *  - orientation as roll / pitch / yaw in radians (no hard cap; the
 *    orientation is *relative* per §14, not absolute)
 *  - timestamp is the canonical `timestamp_ns` from the IMU sample,
 *    not the wall clock; the engine normalises clock domains
 *
 * Per §14: "no intentar obtener posición absoluta integrando
 * indefinidamente aceleración". `MotionState` does not carry a
 * position. It carries the rates and the orientation that an
 * integration step can derive, but it is the engine that decides how
 * to integrate.
 *
 * `MotionState` is optional on [UniversalControllerState]: a wired
 * gamepad that has no IMU reports `null`. A phone acting as a
 * gamepad reports a non-null value when its `Sensor` stream is
 * healthy.
 */
data class MotionState(
    /** Angular velocity around the device x axis, in rad/s. */
    val gyroX: Float,
    /** Angular velocity around the device y axis, in rad/s. */
    val gyroY: Float,
    /** Angular velocity around the device z axis, in rad/s. */
    val gyroZ: Float,
    /** Linear acceleration along the device x axis, in m/s². */
    val accelX: Float,
    /** Linear acceleration along the device y axis, in m/s². */
    val accelY: Float,
    /** Linear acceleration along the device z axis, in m/s². */
    val accelZ: Float,
    /** Roll in radians, relative to the last recenter event. */
    val roll: Float,
    /** Pitch in radians, relative to the last recenter event. */
    val pitch: Float,
    /** Yaw in radians, relative to the last recenter event. */
    val yaw: Float,
    /** The IMU sample's own timestamp in nanoseconds. */
    val sampleTimestampNs: ULong
) {
    companion object {
        /**
         * Validate a motion sample. Per §9 we reject NaN and Infinity
         * on every numeric field. We do **not** reject values outside
         * any particular range because motion sensors legitimately
         * produce large or small numbers (a 16 g accelerometer will
         * report `156.96` on each axis during a hard shake). Range
         * validation belongs in the consumer backend, not in the
         * canonical model.
         */
        fun validate(state: MotionState): ValidationResult {
            val errors = buildList {
                fun check(name: String, v: Float) {
                    when {
                        v.isNaN() -> add(ValidationError.NaN(name))
                        v.isInfinite() -> add(ValidationError.Infinity(name))
                    }
                }
                check("gyroX", state.gyroX)
                check("gyroY", state.gyroY)
                check("gyroZ", state.gyroZ)
                check("accelX", state.accelX)
                check("accelY", state.accelY)
                check("accelZ", state.accelZ)
                check("roll", state.roll)
                check("pitch", state.pitch)
                check("yaw", state.yaw)
            }
            return ValidationResult.of(errors)
        }
    }
}
