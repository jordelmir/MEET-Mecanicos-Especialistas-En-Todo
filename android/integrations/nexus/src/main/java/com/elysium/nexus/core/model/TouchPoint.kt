package com.elysium.nexus.core.model

/**
 * Canonical state of a single touch contact.
 *
 * Per `MASTER_ORDER.md` §9, x and y are in `[0.0, 1.0]` (normalised
 * over the touch surface's logical bounds, not the device's physical
 * pixels — the conversion from pixel to canonical coordinates lives
 * in the touch surface, not the model).
 *
 * `pressure` is in `[0.0, 1.0]`. A device that does not measure
 * pressure (most modern phones) reports a constant `1.0` for any
 * active touch; the model does not pretend to know the difference.
 * Per §13 we must not "afirmar presión física cuando el dispositivo
 * no la mide" — this is enforced by the touch surface reporting
 * `1.0`, not by the model second-guessing the device.
 *
 * `id` is the Android `MotionEvent` pointer id, narrowed to `Int` so
 * the model does not depend on the Android SDK at all. It is the
 * identity that lets the engine match a `pointerDown` to a later
 * `pointerMove` or `pointerUp` for the same finger.
 */
data class TouchPoint(
    val id: Int,
    val x: Float,
    val y: Float,
    val pressure: Float
) {
    companion object {
        /** Inclusive bounds for x, y, and pressure. */
        const val MIN: Float = 0.0f
        const val MAX: Float = 1.0f

        /**
         * Inclusive lower bound for [id]. Android pointer ids are
         * always `>= 0` per the platform contract; we mirror that.
         */
        const val MIN_ID: Int = 0

        /**
         * Inclusive upper bound for [id]. Android has no formal upper
         * bound but in practice the platform never exceeds a handful;
         * we cap at 1024 to keep the model honest and to keep a
         * corrupted id from indexing into something it shouldn't.
         */
        const val MAX_ID: Int = 1024

        /**
         * Validate a single touch point.
         *
         * @return [ValidationResult.Valid] if every field is in
         *   range. Otherwise [ValidationResult.Invalid] listing
         *   every offending field.
         */
        fun validate(point: TouchPoint): ValidationResult {
            val errors = buildList {
                if (point.id !in MIN_ID..MAX_ID) {
                    add(
                        ValidationError.IntegerOutOfRange(
                            field = "id",
                            value = point.id,
                            min = MIN_ID,
                            max = MAX_ID
                        )
                    )
                }
                checkUnit("x", point.x)
                checkUnit("y", point.y)
                checkUnit("pressure", point.pressure)
            }
            return ValidationResult.of(errors)
        }

        private fun MutableList<ValidationError>.checkUnit(
            axis: String,
            value: Float
        ) {
            when {
                value.isNaN() -> add(ValidationError.NaN(axis))
                value.isInfinite() -> add(ValidationError.Infinity(axis))
                value !in MIN..MAX -> add(
                    ValidationError.OutOfRange(
                        field = axis,
                        value = value,
                        min = MIN,
                        max = MAX
                    )
                )
            }
        }
    }
}
