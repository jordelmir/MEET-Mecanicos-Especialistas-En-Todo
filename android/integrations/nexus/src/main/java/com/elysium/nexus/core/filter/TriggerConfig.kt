package com.elysium.nexus.core.filter

/**
 * Configuration for a single trigger's filter pipeline.
 *
 * Triggers are one-directional analogs (`[0, 1]`), so the knob
 * set is a strict subset of the stick's. There is no "outer
 * threshold" (1.0 is the natural max), no "anti-deadzone" (the
 * spec does not list one for triggers), and no "invert" (the
 * trigger only has one direction).
 *
 * The interesting knob is [hairTrigger]: when true, the engine
 * reports a binary "fully pulled / not pulled" state with a
 * tiny activation threshold (a few percent). This is the
 * config competitive FPS players dial in so a hairline pull on
 * the trigger still counts as full deflection.
 *
 * [activationPoint] is the threshold for the digital side. The
 * engine reports `LeftTriggerDigital` (or its right sibling) as
 * pressed when the filtered analog value is `>= activationPoint`.
 * When [hairTrigger] is on, the activation point is overridden
 * to 0.0 — any non-zero pull fires the digital button.
 *
 * [returnCurve] is the curve applied on the *way back* from a
 * higher value to a lower one. Some games want a slower
 * release (so the player can "feather" the trigger without
 * losing the digital press too early); others want a snappy
 * release. Per §13, this is independent of [responseCurve].
 *
 * The defaults match the spec's "no-op" pipeline: deadzone
 * 5%, activation 10%, no hair trigger, linear curve, no
 * reduced range.
 */
data class TriggerConfig(
    /**
     * Inner deadzone in canonical trigger units. A value at or
     * below this is squashed to zero.
     */
    val deadzone: Float = 0.05f,

    /**
     * Activation point for the digital side. The engine
     * reports the corresponding `LeftTriggerDigital` or
     * `RightTriggerDigital` button as pressed when the
     * filtered value is `>= activationPoint`. Must be `>=
     * deadzone`.
     */
    val activationPoint: Float = 0.10f,

    /**
     * When `true`, any pull above the [deadzone] is reported
     * as `1.0` and the digital side is activated at any
     * non-zero pull. This is the "hair trigger" config from
     * §13. The [responseCurve] and [reducedRange] are
     * irrelevant in this mode and the engine short-circuits
     * to `TriggerState.FULL`.
     */
    val hairTrigger: Boolean = false,

    /**
     * Curve applied to the normalised magnitude on the way
     * up. Default is [ResponseCurve.Linear].
     */
    val responseCurve: ResponseCurve = ResponseCurve.Linear,

    /**
     * Curve applied to the normalised magnitude on the way
     * back. Default is [ResponseCurve.Linear]. §13 says
     * "Retorno" is independent of the press curve.
     */
    val returnCurve: ResponseCurve = ResponseCurve.Linear,

    /**
     * Reduced range override. When non-null, the output
     * magnitude is scaled into `[start, endInclusive]`
     * rather than `[0, 1]`. Used by the precision-mode
     * hook: when a precision button is held, the editor
     * swaps in a narrower range, and the host sees small
     * deflections.
     */
    val reducedRange: ClosedFloatingPointRange<Float>? = null
) {
    init {
        require(deadzone in 0f..1f) {
            "deadzone must be in [0, 1] (got $deadzone)."
        }
        require(activationPoint in 0f..1f) {
            "activationPoint must be in [0, 1] (got $activationPoint)."
        }
        require(activationPoint >= deadzone) {
            "activationPoint ($activationPoint) must be >= deadzone ($deadzone)."
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
