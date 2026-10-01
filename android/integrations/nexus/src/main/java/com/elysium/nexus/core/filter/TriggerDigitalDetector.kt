package com.elysium.nexus.core.filter

import com.elysium.nexus.core.model.TriggerState

/**
 * Detects the "digital" side of a trigger.
 *
 * Per `MASTER_ORDER.md` §10, the canonical button set has
 * `LeftTriggerDigital` and `RightTriggerDigital` as separate
 * buttons from the analog `leftTrigger` / `rightTrigger`. The
 * analog side is the *value*; the digital side is the *event*
 * "the trigger has been pulled past the activation point".
 *
 * The detector is a tiny stateful function: it remembers the
 * previous filtered value and the previous digital state, and
 * reports the digital state for the current value. The engine
 * uses the digital state to decide whether to fire a
 * `submitButton(LeftTriggerDigital, true / false)` call.
 *
 * The detector is *separate* from the engine so the engine
 * stays free of trigger-specific knowledge. The engine
 * consumes the digital boolean and the analog value
 * independently; the trigger knob set lives in the filter
 * pipeline.
 */
class TriggerDigitalDetector(
    private val config: TriggerConfig
) {

    private var previousFiltered: TriggerState? = null
    private var previousDigital: Boolean = false

    /**
     * Update the detector with a new filtered value and
     * receive the new digital state.
     *
     * The digital state is `true` when the filtered value is
     * `>= activationPoint` (or, in [TriggerConfig.hairTrigger]
     * mode, when the value is non-zero). The state is
     * hysteresis-free: every call is independent of the
     * previous call. The engine can decide whether to
     * emit a button event by comparing the returned value
     * with the previously returned value (held in
     * [previousDigital] via [lastDigital]).
     */
    fun update(filtered: TriggerState): Boolean {
        val digital = if (config.hairTrigger) {
            filtered.value > 0f
        } else {
            filtered.value >= config.activationPoint
        }
        previousFiltered = filtered
        previousDigital = digital
        return digital
    }

    /**
     * @return the most recent digital state. Useful when the
     *   engine wants to know "is the digital side currently
     *   active" without calling [update].
     */
    fun lastDigital(): Boolean = previousDigital

    /**
     * Reset the detector's history. Called on every state-
     * machine transition out of [EngineState.Active] so the
     * digital side does not retain a stale state across
     * reconnections.
     */
    fun reset() {
        previousFiltered = null
        previousDigital = false
    }
}
