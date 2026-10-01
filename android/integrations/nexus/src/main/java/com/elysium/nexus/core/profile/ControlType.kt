package com.elysium.nexus.core.profile

/**
 * The kind of control a [ControlElement] represents.
 *
 * `MASTER_ORDER.md` §15 lists the elements a user can
 * place on the touch surface: buttons, sticks, triggers,
 * d-pads, touchpads, and the "multi-touch real, chords,
 * slide-in, slide-out, hold, toggle" variants. The enum
 * is the closed taxonomy; the *behavior* of each
 * control is in [ControlElement.behavior] (Phase 1.2+).
 *
 * Phase 1.1 ships a focused subset: the four canonical
 * input surfaces. Phase 1.2 adds `MultiTouchArea`
 * (free-form touch with optional chord detection),
 * `Chord`, and the accessibility variants.
 */
enum class ControlType {
    /** A single button. Maps to one [com.elysium.nexus.core.model.CanonicalButton]. */
    Button,

    /** A virtual stick. The §12 pipeline. */
    Stick,

    /** A virtual trigger. The §13 pipeline. */
    Trigger,

    /** A D-pad (8 directions + center). */
    Dpad,

    /** A multi-touch area (a "touchpad" in the spec). */
    Touchpad
}
