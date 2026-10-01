package com.elysium.nexus.core.filter

/**
 * Configuration for a single stick's filter pipeline.
 *
 * All numeric knobs are clamped at construction so the filter
 * function never has to validate them at runtime. The defaults
 * are a "no-op" pipeline: deadzone 10%, outer threshold 95%, no
 * anti-deadzone, linear curve, sensitivity 1, no inversion, no
 * snap, no saturation, no reduced range. The defaults match what
 * a game developer would call a "raw" stick.
 *
 * Per `MASTER_ORDER.md` §12, every knob here is something the
 * profile editor lets the user dial. The default values are
 * documented per-field so the editor's "reset to default" button
 * matches them.
 */
data class StickConfig(
    /**
     * Inner deadzone radius, in canonical stick units. A magnitude
     * strictly less than or equal to this value is squashed to zero.
     * The §12 spec's primary formula is the same: `if m <=
     * innerDeadzone: output = 0`.
     */
    val innerDeadzone: Float = 0.10f,

    /**
     * Outer threshold radius, in canonical stick units. A
     * magnitude greater than or equal to this value is treated as
     * full deflection. Below the threshold, the magnitude is
     * normalized into `[0, 1]`. Must be strictly greater than
     * [innerDeadzone].
     */
    val outerThreshold: Float = 0.95f,

    /**
     * Anti-deadzone radius. A magnitude that was squashed to zero
     * is rescaled to a non-zero output, like a "stirring zone" near
     * the centre. A value of `0.0` is the default (no anti-zone);
     * `0.05` would be a 5% anti-deadzone.
     */
    val antiDeadzone: Float = 0.0f,

    /**
     * The response curve applied to the normalised magnitude
     * after the deadzone. Default is [ResponseCurve.Linear].
     */
    val responseCurve: ResponseCurve = ResponseCurve.Linear,

    /**
     * Multiplier applied to the final output magnitude. A value
     * greater than `1` amplifies; less than `1` attenuates. The
     * spec is "sensibilidad" (sensitivity). Default `1.0` is
     * identity.
     */
    val sensitivity: Float = 1.0f,

    /**
     * Invert the horizontal axis. Useful for left-handed users
     * and for a D-pad-as-stick setup that is rotated 90°.
     */
    val invertX: Boolean = false,

    /**
     * Invert the vertical axis. The convention is "up is
     * positive"; some games prefer "down is positive". The user
     * can flip.
     */
    val invertY: Boolean = false,

    /**
     * Snap-to-cardinal strength, in canonical units. A non-zero
     * value pulls small off-axis magnitudes toward the nearest
     * cardinal direction. `0.0` (default) disables snap.
     */
    val snapToCardinal: Float = 0.0f,

    /**
     * Output saturation. Magnitudes above `1.0` are clipped. A
     * value of `1.0` (default) means the stick never exceeds the
     * canonical range. A value greater than `1.0` allows the
     * intermediate `[0, 1]` curve output to be amplified before
     * clipping.
     */
    val saturation: Float = 1.0f,

    /**
     * Reduced range override. When non-null, the output magnitude
     * is scaled into `[reducedRange.start, reducedRange.end]`
     * rather than `[0, 1]`. This is the "precision mode" hook:
     * when a precision button is held, the editor swaps in a
     * narrower range, and the host sees small deflections.
     */
    val reducedRange: ClosedFloatingPointRange<Float>? = null,

    /**
     * Behavioural mode. Drives the choice of pipeline; see
     * [StickMode]. 0.3 only fully implements [StickMode.FixedCenter];
     * the other modes are scaffolded in 0.4.
     */
    val mode: StickMode = StickMode.FixedCenter
) {
    init {
        require(innerDeadzone in 0f..1f) {
            "innerDeadzone must be in [0, 1] (got $innerDeadzone)."
        }
        require(outerThreshold in 0f..1f) {
            "outerThreshold must be in [0, 1] (got $outerThreshold)."
        }
        require(outerThreshold > innerDeadzone) {
            "outerThreshold ($outerThreshold) must be strictly greater than innerDeadzone ($innerDeadzone)."
        }
        require(antiDeadzone in 0f..1f) {
            "antiDeadzone must be in [0, 1] (got $antiDeadzone)."
        }
        require(sensitivity >= 0f) {
            "sensitivity must be non-negative (got $sensitivity)."
        }
        require(snapToCardinal in 0f..1f) {
            "snapToCardinal must be in [0, 1] (got $snapToCardinal)."
        }
        require(saturation > 0f) {
            "saturation must be positive (got $saturation)."
        }
        reducedRange?.let {
            require(it.start in 0f..1f && it.endInclusive in 0f..1f) {
                "reducedRange must be a subrange of [0, 1] (got ${it.start}..${it.endInclusive})."
            }
            require(it.start <= it.endInclusive) {
                "reducedRange start (${it.start}) must be <= endInclusive (${it.endInclusive})."
            }
        }
    }
}
