package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.TriggerState

/**
 * The trigger filter pipeline.
 *
 * The pipeline applies the deadzone, optionally the hair-trigger
 * short-circuit, the response curve (or the return curve, depending
 * on the direction of motion), and the reduced-range scaling. It
 * is a single pure function so it is trivially testable from the
 * JVM, and so the engine can call it on every trigger sample
 * without any state.
 *
 * The §13 spec lists:
 *
 *  - digital
 *  - slider
 *  - pressure approximation
 *  - dual stage
 *  - hair trigger
 *  - recorrido configurable
 *  - curva independiente
 *  - punto de activación
 *  - zona muerta
 *  - retorno
 *
 * 0.5 ships the digital, hair-trigger, activation-point, deadzone,
 * response-curve, return-curve, and reduced-range knobs. The
 * "slider" / "pressure approximation" / "dual stage" variants
 * land in 0.6 as additional trigger modes; the data shape is
 * ready.
 */
object TriggerFilters {

    /**
     * Apply the filter pipeline to a raw trigger sample.
     *
     * @param raw the raw trigger sample (canonical `[0, 1]`
     *   range).
     * @param config the per-trigger configuration.
     * @param previousFiltered the previously-filtered value, used
     *   to choose between [TriggerConfig.responseCurve] (on the
     *   way up, when `raw >= previousFiltered`) and
     *   [TriggerConfig.returnCurve] (on the way back). When
     *   `null` (first call), the response curve is used.
     * @return the filtered trigger sample, still in the canonical
     *   `[0, 1]` range.
     */
    fun apply(
        raw: TriggerState,
        config: TriggerConfig,
        previousFiltered: TriggerState? = null
    ): TriggerState {
        // Step 0: degenerate input.
        if (raw.value == 0f) return TriggerState.RELEASED

        // Step 1: deadzone.
        if (raw.value <= config.deadzone) {
            return TriggerState.RELEASED
        }

        // Step 2: hair trigger short-circuit. Any pull above
        // the deadzone becomes a full pull. The activation point
        // is implicit at 0 — see TriggerDigitalDetector.
        if (config.hairTrigger) {
            return TriggerState.FULL
        }

        // Step 3: normalise the magnitude into [0, 1] and apply
        // the response curve (or return curve, depending on
        // direction).
        val span = 1f - config.deadzone
        val normalised = ((raw.value - config.deadzone) / span).coerceIn(0f, 1f)
        val onTheWayUp = previousFiltered == null || raw.value >= previousFiltered.value
        val curve = if (onTheWayUp) config.responseCurve else config.returnCurve
        val curved = curve.apply(normalised)

        // Step 4: reduced range. When configured, rescale the
        // [0, 1] magnitude into the configured subrange.
        val ranged = config.reducedRange?.let { range ->
            val spanR = range.endInclusive - range.start
            range.start + curved.coerceIn(0f, 1f) * spanR
        } ?: curved

        // Step 5: clamp to the canonical range. The pipeline
        // never produces out-of-band values, but the clamp is a
        // belt-and-braces guard.
        val clamped = ranged.coerceIn(0f, 1f)
        return TriggerState(clamped)
    }
}
