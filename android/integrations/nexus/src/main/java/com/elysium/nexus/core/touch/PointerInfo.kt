package com.elysium.nexus.core.touch

import com.elysium.nexus.core.model.TouchPoint

/**
 * A single pointer sample, decoupled from the Android
 * `MotionEvent` API.
 *
 * `MASTER_ORDER.md` §11 specifies the touch surface as the
 * primary input source. The Android SDK exposes pointer data
 * via `MotionEvent`, but the engine consumes a
 * `TouchCollection` of `TouchPoint` (Phase 0.2). The
 * translation between the two happens here, in
 * [PointerInfo], so:
 *
 *  - the dispatcher ([TouchEventDispatcher]) can be unit-
 *    tested from the JVM without an `android.jar` at runtime;
 *  - the touch surface `View` ([com.elysium.nexus.input.TouchSurfaceView])
 *    can call into the dispatcher with already-parsed
 *    `PointerInfo` values, with the `MotionEvent`-specific
 *    details (history samples, tool type, flags) handled
 *    in one place.
 *
 * Coordinates are canonical: `x` and `y` are in `[0, 1]`
 * over the surface's logical bounds. `pressure` is in
 * `[0, 1]`. The View does the normalisation; tests can
 * supply pre-normalised values.
 */
data class PointerInfo(
    val id: Int,
    val x: Float,
    val y: Float,
    val pressure: Float
) {
    /**
     * Convert to the canonical [TouchPoint] that the engine
     * consumes. The conversion is a straight field copy; the
     * canonical range checks live in [TouchPoint.validate]
     * and in the engine's commit path.
     */
    fun toTouchPoint(): TouchPoint = TouchPoint(
        id = id,
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        pressure = pressure.coerceIn(0f, 1f)
    )
}
