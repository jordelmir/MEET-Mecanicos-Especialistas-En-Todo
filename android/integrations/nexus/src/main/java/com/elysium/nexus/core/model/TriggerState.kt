package com.elysium.nexus.core.model

/**
 * Canonical state of a single analog trigger.
 *
 * Per `MASTER_ORDER.md` §9, the canonical range is `[0.0, 1.0]`. Per
 * §13, downstream code applies hair-trigger cutoffs, dual-stage
 * thresholds, response curves, and (where supported by the backend)
 * resistance. The canonical state is **raw** — those filters live in
 * the engine, not in the model.
 *
 * `TriggerState` is a data class instead of a value class because
 * trigger values typically have a digital fallback (`value > 0.5`)
 * that benefits from a wide API surface; we want `copy()`, `toString`,
 * and structural equality all the way down.
 */
data class TriggerState(val value: Float) {

    companion object {
        /** Trigger at rest, no pull. The neutral value per §32. */
        val RELEASED: TriggerState = TriggerState(0f)

        /** Trigger fully depressed. */
        val FULL: TriggerState = TriggerState(1f)

        /** Inclusive lower bound. */
        const val MIN: Float = 0.0f

        /** Inclusive upper bound. */
        const val MAX: Float = 1.0f

        /**
         * Validate a trigger sample.
         *
         * @return [ValidationResult.Valid] if the value is finite and
         *   in `[0.0, 1.0]`. Otherwise [ValidationResult.Invalid].
         */
        fun validate(state: TriggerState): ValidationResult {
            val errors = buildList {
                when {
                    state.value.isNaN() ->
                        add(ValidationError.NaN("value"))
                    state.value.isInfinite() ->
                        add(ValidationError.Infinity("value"))
                    state.value !in MIN..MAX -> add(
                        ValidationError.OutOfRange(
                            field = "value",
                            value = state.value,
                            min = MIN,
                            max = MAX
                        )
                    )
                }
            }
            return ValidationResult.of(errors)
        }
    }
}
