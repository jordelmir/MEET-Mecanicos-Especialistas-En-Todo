package com.elysium.nexus.core.touch

/**
 * The pointer-level actions the dispatcher understands.
 *
 * Mirrored from the Android `MotionEvent` action constants
 * for the events that the §11 pipeline cares about. We use
 * a closed enum (instead of an `Int`) so the dispatcher's
 * `when` is exhaustive at compile time.
 */
enum class TouchAction {
    /**
     * The first pointer went down. The [pointers] list
     * contains exactly one entry — the new pointer. The
     * dispatcher's internal state is reset.
     */
    Down,

    /**
     * An additional pointer went down (already at least one
     * pointer is active). The [pointers] list contains the
     * new pointer only — the existing ones are *not*
     * re-emitted.
     */
    PointerDown,

    /**
     * One or more pointers moved. The [pointers] list
     * contains every active pointer with its current
     * coordinates. The dispatcher emits a callback for
     * each.
     */
    Move,

    /**
     * A non-primary pointer went up. The [pointers] list
     * contains the released pointer only. The remaining
     * active pointers are not re-emitted.
     */
    PointerUp,

    /**
     * The last active pointer went up. The [pointers] list
     * contains the released pointer only. The dispatcher
     * emits a removal callback for it (and for any other
     * pointer that may have been left over from a malformed
     * event sequence).
     */
    Up,

    /**
     * The gesture was cancelled by the platform (e.g. a
     * system gesture, a parent intercept, an Activity
     * pause). The [pointers] list is the set of pointers
     * that were active at the time of cancellation. The
     * dispatcher emits a removal callback for each. Per
     * §11, every cancellation must produce a neutral
     * state.
     */
    Cancel
}
