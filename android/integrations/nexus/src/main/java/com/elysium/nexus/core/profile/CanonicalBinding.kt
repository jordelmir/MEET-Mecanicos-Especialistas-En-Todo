package com.elysium.nexus.core.profile

import com.elysium.nexus.core.engine.StickSide
import com.elysium.nexus.core.model.CanonicalButton

/**
 * What a [ControlElement] is *bound to* in the canonical
 * model.
 *
 * `MASTER_ORDER.md` §15 says "Cada elemento tendrá...
 * `binding: CanonicalBinding`". The binding is the
 * mapping from the user-facing control (a button drawn
 * on the touch surface) to the canonical action (one of
 * the 23 `CanonicalButton`s, one of the two sticks,
 * one of the two triggers, or a custom press). The
 * `Mapping and Profile Engine` (§5) consumes the
 * binding to translate a touch into a `submitButton` /
 * `submitStick` / `submitTrigger` call on the engine.
 *
 * Phase 1.1 ships the `Button` binding (a single canonical
 * button) and the `Neutralize` action (the §38 button).
 * The other bindings land in 1.2.
 *
 * ## Why a sealed class
 *
 * A sealed class with exhaustive `when` lets the
 * `Mapping and Profile Engine` (Phase 1.2+) match on
 * every binding variant without an `else` branch. A
 * future contributor who adds a new binding variant
 * gets a compile error in the engine until the
 * `when` is updated.
 */
sealed class CanonicalBinding {
    /**
     * The control is bound to a single canonical button
     * (e.g. `South`, `North`, `MenuPrimary`). The
     * engine calls `submitButton(binding.button, true)`
     * on press and `submitButton(binding.button, false)`
     * on release.
     */
    data class Button(val button: CanonicalButton) : CanonicalBinding()

    /**
     * The control is bound to a virtual stick. The
     * `Mapping and Profile Engine` (1.2+) reads the
     * touch coordinates inside the control's rect and
     * submits them to the engine's `submitStick`.
     */
    data class Stick(val side: StickSide) : CanonicalBinding()

    /**
     * The control is bound to a virtual trigger.
     * The engine submits `submitTrigger(side, value)`
     * based on the touch's vertical position inside
     * the control's rect.
     */
    data class Trigger(val side: StickSide) : CanonicalBinding()

    /**
     * The control is the §38 Neutralize button. When
     * pressed, the engine emits a neutral frame. The
     * activity wires this to the "Neutralize" button
     * in the editor's toolbar (and the editor can
     * place a Neutralize button on the canvas as a
     * one-tap escape).
     */
    object Neutralize : CanonicalBinding()
}
