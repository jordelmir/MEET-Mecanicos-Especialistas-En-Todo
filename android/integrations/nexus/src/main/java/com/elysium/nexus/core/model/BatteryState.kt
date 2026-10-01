package com.elysium.nexus.core.model

/**
 * Canonical battery state.
 *
 * Per `MASTER_ORDER.md` §9, this is one of the optional fields of
 * [UniversalControllerState]; the model treats the absence of a
 * battery as `null`, not as a magic sentinel.
 *
 * `level` is in `[0, 100]` (integer percent). `isCharging` is the
 * charging-source-OK boolean from the Android BatteryManager / a
 * future receiver-side power management IC.
 *
 * The model deliberately does not carry voltage, current, health,
 * temperature, or technology. Those are useful for a diagnostics
 * screen but not for the canonical state. They land in the
 * `telemetry-core` crate / module when the telemetry panel of
 * `MASTER_ORDER.md` §34 is built out.
 */
data class BatteryState(
    val level: Int,
    val isCharging: Boolean
) {
    companion object {
        /** Lower bound on the integer percent. */
        const val MIN_LEVEL: Int = 0
        /** Upper bound on the integer percent. */
        const val MAX_LEVEL: Int = 100

        /** Validate a battery state. */
        fun validate(state: BatteryState): ValidationResult {
            val errors = buildList {
                if (state.level !in MIN_LEVEL..MAX_LEVEL) {
                    add(
                        ValidationError.IntegerOutOfRange(
                            field = "level",
                            value = state.level,
                            min = MIN_LEVEL,
                            max = MAX_LEVEL
                        )
                    )
                }
            }
            return ValidationResult.of(errors)
        }
    }
}
