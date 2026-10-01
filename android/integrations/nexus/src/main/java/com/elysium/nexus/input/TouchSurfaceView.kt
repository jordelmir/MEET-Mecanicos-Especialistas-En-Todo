package com.elysium.nexus.input

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.elysium.nexus.core.model.TouchPoint
import com.elysium.nexus.core.touch.PointerInfo
import com.elysium.nexus.core.touch.TouchAction
import com.elysium.nexus.core.touch.TouchEventDispatcher

/**
 * A specialised `View` that owns the touch input stream.
 *
 * Per `MASTER_ORDER.md` §11:
 *
 * > Compose puede controlar navegación, ajustes, editor, listas,
 * > pantallas no críticas. El camino de entrada analógica
 * > deberá evaluarse con una View especializada porque
 * > `MotionEvent` expone muestras históricas, permite
 * > seguimiento preciso por `pointerId`, reduce
 * > recomposiciones, facilita buffers reutilizables, da
 * > mayor control sobre cancelaciones.
 *
 * This is the View. It does the Android-specific work of
 * turning `MotionEvent`s into normalised `PointerInfo`s and
 * forwarding them to the [TouchEventDispatcher], which is
 * pure Kotlin and unit-tested from the JVM.
 *
 * ## Why a separate dispatcher
 *
 * The dispatcher is what we test. It accepts `(action,
 * pointers)` and emits `(id, point?)` callbacks. The View is
 * a thin shell that:
 *
 *  1. parses `MotionEvent` into the action / pointer list
 *     shape the dispatcher expects;
 *  2. normalises coordinates to `[0, 1]` over the view's
 *     current `width` × `height`;
 *  3. clamps `pressure` to `[0, 1]`;
 *  4. forwards the result to the dispatcher.
 *
 * The View itself is not unit-tested (it would require
 * Robolectric or a real `Activity`). The dispatcher is
 * fully tested.
 *
 * ## Testability bridge
 *
 * The View exposes [onTouchPointChange] as a public
 * `var`. Production wires it to
 * `engine.submitTouchPoint`. Tests do not instantiate the
 * View; they instantiate the dispatcher directly with a
 * recording callback.
 *
 * ## Pipeline
 *
 * ```
 * MotionEvent
 *     ↓
 * action = event.actionMasked
 * pointers = (0 until event.pointerCount).map { ... }
 *     ↓
 * TouchEventDispatcher.process(action, pointers)
 *     ↓
 * callback(id, point?)
 *     ↓
 * engine.submitTouchPoint(id, point)
 * ```
 */
class TouchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * The callback the dispatcher fires for every per-pointer
     * add / update / remove. Production wires this to the
     * engine; the View does not know or care which.
     *
     * `point` is `null` when the pointer has been released
     * or cancelled.
     *
     * `t0Ns` is the platform-level timestamp at which the
     * underlying `MotionEvent` was delivered (§30 T0). The
     * View propagates it so the engine can record the
     * per-event processing latency (T2 - T0).
     */
    var onTouchPointChange: (id: Int, point: TouchPoint?, t0Ns: Long?) -> Unit = { _, _, _ -> }

    private val dispatcher: TouchEventDispatcher =
        TouchEventDispatcher { id, point, t0Ns ->
            onTouchPointChange(id, point, t0Ns)
        }

    /**
     * Receive a `MotionEvent` from the platform. The View
     * translates it into the dispatcher's action / pointer
     * shape and dispatches.
     *
     * @return `true` to indicate the event was consumed. We
     *   always consume touch events that reach this view;
     *   the parent (or the activity) does not need to
     *   intercept.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width
        val h = height
        val action = mapAction(event.actionMasked)
        if (action == null) {
            // An action we don't model (e.g. HOVER_ENTER). We
            // still return true to keep the stream attached
            // to this view, but we do not dispatch.
            return true
        }
        // §30 T0: the platform-level timestamp at which
        // the MotionEvent was delivered. We use
        // `SystemClock.elapsedRealtimeNanos()` (the
        // Android-standard monotonic clock) so the diff
        // to the engine's T2 is in the same units.
        val t0Ns = SystemClock.elapsedRealtimeNanos()
        val pointers = (0 until event.pointerCount).map { i ->
            // Normalise over the view's current bounds. The
            // view's `width` / `height` are reported in
            // pixels; the canonical range is `[0, 1]`. The
            // view's bounds are used, not the parent, so
            // touches on this view are relative to this
            // view's coordinate system, not the screen.
            val px = event.getX(i)
            val py = event.getY(i)
            val x = if (w > 0) (px / w).coerceIn(0f, 1f) else 0f
            val y = if (h > 0) (py / h).coerceIn(0f, 1f) else 0f
            val pressure = event.getPressure(i).coerceIn(0f, 1f)
            PointerInfo(
                id = event.getPointerId(i),
                x = x,
                y = y,
                pressure = pressure
            )
        }
        dispatcher.process(action, pointers, t0Ns)
        return true
    }

    /**
     * Map a `MotionEvent` action code to a [TouchAction]. The
     * dispatcher's enum is closed; actions we do not model
     * (hover events, scroll events, button-while-not-touching
     * events) return `null` and the caller discards them.
     */
    private fun mapAction(raw: Int): TouchAction? = when (raw) {
        MotionEvent.ACTION_DOWN -> TouchAction.Down
        MotionEvent.ACTION_POINTER_DOWN -> TouchAction.PointerDown
        MotionEvent.ACTION_MOVE -> TouchAction.Move
        MotionEvent.ACTION_POINTER_UP -> TouchAction.PointerUp
        MotionEvent.ACTION_UP -> TouchAction.Up
        MotionEvent.ACTION_CANCEL -> TouchAction.Cancel
        else -> null
    }

    /**
     * Force-reset the touch dispatcher. Called by the engine
     * (or the activity) on every state-machine transition
     * out of `Active` so a touch in flight does not leave
     * a stuck pointer on the host. Per §38, this is part of
     * the release-blocker contract.
     */
    fun resetTouches() {
        dispatcher.reset()
    }
}
