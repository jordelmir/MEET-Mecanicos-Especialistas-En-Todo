package com.elysium.nexus.core.model

/**
 * Sealed result of validating a canonical state value.
 *
 * `MASTER_ORDER.md` §9 mandates:
 *
 *   - NaN rejected
 *   - Infinity rejected
 *   - Out-of-range rejected
 *   - Regressive timestamps rejected (handled by the engine, not the
 *     model)
 *   - Repeated sequences rejected (handled by the engine, not the
 *     model)
 *   - Incompatible states rejected
 *   - Oversized frames rejected
 *
 * The model validates *the value*; the engine validates *the value
 * against the previous value* (sequence / timestamp regressions,
 * state-to-state transition legality).
 */
sealed class ValidationResult {

    /** Every value is in range and finite. */
    object Valid : ValidationResult()

    /** At least one value failed. [errors] is never empty. */
    data class Invalid(val errors: List<ValidationError>) : ValidationResult() {
        init {
            require(errors.isNotEmpty()) {
                "ValidationResult.Invalid requires a non-empty error list."
            }
        }
    }

    companion object {
        /** Convenience: empty error list ⇒ valid. */
        fun of(errors: List<ValidationError>): ValidationResult =
            if (errors.isEmpty()) Valid else Invalid(errors)
    }
}

/**
 * Sealed taxonomy of validation errors.
 *
 * The [field] name on numeric variants is the field path within the
 * state being validated. The path is dot-delimited (e.g.
 * `"leftStick.x"`, `"touches[2].pressure"`).
 *
 * Engine-level errors (sequence regression, timestamp regression,
 * state-to-state incompatibilities) are kept off this sealed class
 * because they are not "this value is bad" — they are "this value is
 * inconsistent with the previous one". They live in the engine.
 */
sealed class ValidationError {

    /** A `Float` was NaN. */
    data class NaN(val field: String) : ValidationError()

    /** A `Float` was `+Inf` or `-Inf`. */
    data class Infinity(val field: String) : ValidationError()

    /** A `Float` was outside `[min, max]`. */
    data class OutOfRange(
        val field: String,
        val value: Float,
        val min: Float,
        val max: Float
    ) : ValidationError()

    /** A numeric value was outside its integer range. */
    data class IntegerOutOfRange(
        val field: String,
        val value: Int,
        val min: Int,
        val max: Int
    ) : ValidationError()

    /** A collection contained more elements than the canonical frame allows. */
    data class FrameTooLarge(
        val field: String,
        val actual: Int,
        val max: Int
    ) : ValidationError()

    /**
     * Generic catch-all for state-internal inconsistencies that don't
     * fit the numeric buckets (e.g. a touch with negative pressure
     * alongside a touch with NaN pressure is a single
     * "incompatible touches" error rather than two).
     */
    data class IncompatibleState(
        val field: String,
        val reason: String
    ) : ValidationError()
}
