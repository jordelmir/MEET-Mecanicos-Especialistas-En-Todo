package com.elysium.nexus.core.model

/**
 * Canonical state of a single analog stick.
 *
 * Per `MASTER_ORDER.md` §9, the canonical range is `[-1.0, +1.0]` on
 * both axes. Per §12, downstream code applies deadzones, anti-deadzones,
 * response curves, and saturation. The canonical state is **raw** —
 * those filters live in the engine, not in the model.
 *
 * `x` is conventionally the horizontal axis (right is positive);
 * `y` is the vertical axis (up is positive). The coordinate
 * convention is the one `MotionEvent`-derived touch surfaces feed in
 * (after normalisation); flipping the sign is the job of a per-stick
 * inversion in the profile.
 *
 * `StickState` is immutable. Use [copy] to produce a new value, never
 * mutate fields.
 */
data class StickState(val x: Float, val y: Float) {

    companion object {
        /** Stick at rest, no deflection. The neutral value per §32. */
        val NEUTRAL: StickState = StickState(0f, 0f)

        /** Maximum deflection toward `+x +y`. */
        val MAX_XY: StickState = StickState(1f, 1f)

        /** Maximum deflection toward `-x -y`. */
        val MIN_XY: StickState = StickState(-1f, -1f)

        /** Inclusive lower bound on each axis. */
        const val MIN: Float = -1.0f

        /** Inclusive upper bound on each axis. */
        const val MAX: Float = 1.0f

        /**
         * Validate a stick sample.
         *
         * @return [ValidationResult.Valid] if both axes are finite and
         *   in `[-1.0, +1.0]`. Otherwise a [ValidationResult.Invalid]
         *   listing every offending axis.
         */
        fun validate(state: StickState): ValidationResult {
            val errors = buildList {
                checkAxis("x", state.x)
                checkAxis("y", state.y)
            }
            return ValidationResult.of(errors)
        }

        private fun MutableList<ValidationError>.checkAxis(
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
