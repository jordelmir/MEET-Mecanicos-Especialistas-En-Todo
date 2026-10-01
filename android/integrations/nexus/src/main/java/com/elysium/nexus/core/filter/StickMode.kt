package com.elysium.nexus.core.filter

/**
 * The behavioural mode a stick operates in, per
 * `MASTER_ORDER.md` §12.
 *
 * Each mode is a "what does the user mean" interpretation of the
 * raw two-axis input. The mapping from mode to the actual filter
 * pipeline is implemented in [StickFilters]; this enum is the
 * control surface.
 */
enum class StickMode {
    /**
     * Classic gamepad stick. Pulls and holds a direction; the
     * engine reports the current deflection, the host maps it to
     * a character / camera. This is the default.
     */
    FixedCenter,

    /**
     * The stick does *not* auto-recenter. The user starts at
     * whatever deflection the stick had on first contact and only
     * changes from there. Useful for accessibility (tremor
     * filtering) and for trackpad-style emulations.
     *
     * 0.3 implements the centre as "where the user is currently
     * holding the stick" — i.e. the first sample establishes a
     * virtual centre and subsequent samples are deltas from that
     * virtual centre. The drift model that compensates for the
     * user's hand moving lands in 0.4.
     */
    FloatingCenter,

    /**
     * Fixed for fine inputs (small deflections), floating for
     * large ones. Same accessibility intent as [FloatingCenter]
     * but with a smoother transition. 0.4.
     */
    HybridCenter,

    /**
     * The stick is a trackpad: the user drags their finger and
     * the stick reports *relative* motion. The host interprets
     * the deltas as camera / cursor movement, not as a held
     * direction. 0.4.
     */
    RelativeTrackpad,

    /**
     * The user flicks the stick to turn and holds for fine aim.
     * Implemented as a state machine in [StickFilters] — flick
     * (high-speed short release) → one-shot rotation, hold →
     * fine-aim stick. 0.4.
     */
    FlickStick,

    /**
     * Stick + gyroscope fusion. Stick handles large movements,
     * gyro handles small ones. Needs [com.elysium.nexus.core.model.MotionState]
     * — implemented in 0.4 once the engine has the motion
     * pipeline.
     */
    GyroAssisted,

    /**
     * When a button is held, the stick reports a reduced range
     * (e.g. 50% deflection → 25% output) for precision aim. 0.4.
     */
    Precision
}
