package com.elysium.nexus.core.model

/**
 * The root canonical state.
 *
 * This is the contract the rest of Elysium Nexus consumes. It mirrors
 * the Rust struct in `MASTER_ORDER.md` §9 verbatim:
 *
 * ```
 * pub struct UniversalControllerState {
 *     pub sequence: u64,
 *     pub timestamp_ns: u64,
 *     pub buttons: ButtonSet,
 *     pub dpad: DpadState,
 *     pub left_stick: StickState,
 *     pub right_stick: StickState,
 *     pub left_trigger: f32,
 *     pub right_trigger: f32,
 *     pub touches: TouchCollection,
 *     pub motion: Option<MotionState>,
 *     pub battery: Option<BatteryState>,
 * }
 * ```
 *
 * In Kotlin:
 *
 *  - `sequence` and `timestamp_ns` are [ULong] (the spec is `u64`).
 *  - Buttons are a [ButtonSet] (a 64-bit bitset, see its KDoc).
 *  - Triggers are [TriggerState] (data class) instead of bare `f32`
 *    so callers get a typed API; the value field is still `f32`.
 *  - `Option<…>` is `Kotlin`'s nullable `…?`.
 *
 * ## Why a single immutable snapshot
 *
 * §19.3 requires the realtime plane to follow "latest-wins"
 * semantics: "un evento analógico antiguo nunca debe bloquear un
 * valor reciente". The cheapest way to enforce that is to make the
 * state immutable and to make the engine atomic-swap the state into
 * a [kotlinx.coroutines.flow.MutableStateFlow] (or an equivalent
 * single-writer channel) on every update. A consumer that is slow to
 * read never blocks the writer; the writer's `compareAndSet` either
 * lands the new value or is dropped because a newer value already
 * arrived.
 *
 * ## Validation contract
 *
 * The [validate] function checks every numeric field, the touch
 * collection, the optional motion state, and the optional battery
 * state. It does **not** check sequence-vs-previous-sequence or
 * timestamp-vs-previous-timestamp: that comparison is a property of
 * the *engine*, not of an individual state, and lives in the engine
 * when it lands in 0.3 / 0.4.
 */
data class UniversalControllerState(
    val sequence: ULong,
    val timestampNs: ULong,
    val buttons: ButtonSet,
    val dpad: DpadState,
    val leftStick: StickState,
    val rightStick: StickState,
    val leftTrigger: TriggerState,
    val rightTrigger: TriggerState,
    val touches: TouchCollection,
    val motion: MotionState? = null,
    val battery: BatteryState? = null
) {
    companion object {
        /**
         * A safe default: every analog value at rest, no buttons
         * held, no touches, no motion, no battery. This is the
         * state the engine emits on every transition out of `Active`
         * per §32 / §38 — the disconnect-neutrals test relies on it.
         */
        fun neutral(
            sequence: ULong = 0uL,
            timestampNs: ULong = 0uL
        ): UniversalControllerState = UniversalControllerState(
            sequence = sequence,
            timestampNs = timestampNs,
            buttons = ButtonSet.EMPTY,
            dpad = DpadState.Center,
            leftStick = StickState.NEUTRAL,
            rightStick = StickState.NEUTRAL,
            leftTrigger = TriggerState.RELEASED,
            rightTrigger = TriggerState.RELEASED,
            touches = TouchCollection.EMPTY,
            motion = null,
            battery = null
        )

        /**
         * Validate every field of the state. Returns
         * [ValidationResult.Valid] if every sub-validator passes, or
         * [ValidationResult.Invalid] with the concatenated error list
         * otherwise.
         */
        fun validate(state: UniversalControllerState): ValidationResult {
            val errors = buildList {
                // Numeric axes
                collectSubErrors(StickState.validate(state.leftStick), "leftStick") { addAll(it) }
                collectSubErrors(StickState.validate(state.rightStick), "rightStick") { addAll(it) }
                collectSubErrors(TriggerState.validate(state.leftTrigger), "leftTrigger") { addAll(it) }
                collectSubErrors(TriggerState.validate(state.rightTrigger), "rightTrigger") { addAll(it) }
                collectSubErrors(TouchCollection.validate(state.touches), "touches") { addAll(it) }

                // Optional motion
                state.motion?.let { motion ->
                    collectSubErrors(MotionState.validate(motion), "motion") { addAll(it) }
                }

                // Optional battery
                state.battery?.let { battery ->
                    collectSubErrors(BatteryState.validate(battery), "battery") { addAll(it) }
                }
            }
            return ValidationResult.of(errors)
        }

        /**
         * Helper: take a sub-validation result, rewrite each error's
         * field path with a [prefix], and forward the errors to
         * [sink]. Sub-results that are [ValidationResult.Valid] are
         * dropped.
         */
        private inline fun MutableList<ValidationError>.collectSubErrors(
            sub: ValidationResult,
            prefix: String,
            sink: (List<ValidationError>) -> Unit
        ) {
            if (sub is ValidationResult.Invalid) {
                sink(
                    sub.errors.map { err ->
                        when (err) {
                            is ValidationError.NaN ->
                                err.copy(field = "$prefix.${err.field}")
                            is ValidationError.Infinity ->
                                err.copy(field = "$prefix.${err.field}")
                            is ValidationError.OutOfRange ->
                                err.copy(field = "$prefix.${err.field}")
                            is ValidationError.IntegerOutOfRange ->
                                err.copy(field = "$prefix.${err.field}")
                            is ValidationError.FrameTooLarge ->
                                err.copy(field = "$prefix.${err.field}")
                            is ValidationError.IncompatibleState ->
                                err.copy(field = "$prefix.${err.field}")
                        }
                    }
                )
            }
        }
    }
}
