package com.elysium.nexus.core.touch

/**
 * Pure-Kotlin touch event dispatcher.
 *
 * Sits between the Android `MotionEvent` stream (which lives
 * in [com.elysium.nexus.input.TouchSurfaceView]) and the
 * engine's `submitTouchPoint`. The dispatcher's job is to
 * turn the action / pointer stream into a stream of
 * per-pointer add / update / remove callbacks, in a way
 * that is testable from the JVM without an `android.jar`.
 *
 * ## Why a class with a callback
 *
 * Per the agent-memory lesson on `Context`-dependent
 * code, the dispatcher exposes a narrow callback shape
 * (`(id: Int, point: TouchPoint?, t0Ns: Long?) -> Unit`)
 * that captures only the values the engine actually
 * consumes. The production caller wires the callback to
 * `engine.submitTouchPoint`; tests wire it to a recording
 * lambda. The dispatcher itself has no dependency on
 * `CanonicalInputEngine`, on Android, on coroutines, or
 * on any global state.
 *
 * The optional `t0Ns` parameter is the T0 timestamp from
 * the §30 latency budget — the time the platform
 * delivered the `MotionEvent` to the view. The engine
 * uses it to compute the per-event processing latency
 * (T2 - T0) and feed it to a `LatencyTracker`. The
 * dispatcher does not interpret T0; it just propagates
 * it.
 *
 * ## State model
 *
 * The dispatcher holds a `mutableMapOf<Int, PointerInfo>`
 * of "currently active pointers". Every [process] call
 * updates the map and emits the appropriate callbacks. The
 * map is the only state; resetting it (e.g. on
 * `TouchAction.Cancel` or on an explicit [reset] call)
 * emits a removal callback for every still-tracked
 * pointer. This guarantees that the engine never holds a
 * stale touch when the dispatcher is reset.
 */
class TouchEventDispatcher(
    private val callback: (
        id: Int,
        point: com.elysium.nexus.core.model.TouchPoint?,
        t0Ns: Long?
    ) -> Unit
) {

    private val active: MutableMap<Int, PointerInfo> = LinkedHashMap()

    /**
     * @return the number of pointers the dispatcher currently
     *   considers active. Tests use this to assert
     *   bookkeeping.
     */
    fun activeCount(): Int = active.size

    /**
     * @return the IDs of the currently-active pointers, in
     *   the order they were added. Tests use this to assert
     *   the dispatcher's view of the world.
     */
    fun activeIds(): List<Int> = active.keys.toList()

    /**
     * Process a single event. The action and the [pointers]
     * list together describe what changed; the dispatcher
     * applies the change to its state and emits per-pointer
     * callbacks as a side effect.
     *
     * [t0Ns] is the platform-level timestamp at which the
     * underlying `MotionEvent` was delivered. The
     * dispatcher propagates it to every callback so the
     * engine can compute the per-event processing
     * latency. When the dispatcher is reset or called
     * outside the platform's `onTouchEvent`, [t0Ns] is
     * `null`; the engine treats `null` as "do not
     * record".
     *
     * Per §11, every event that *removes* a pointer emits
     * a `null` callback. Per §38, the dispatcher's state is
     * always coherent: the engine never sees a "removed"
     * pointer followed by a `Move` event that re-asserts
     * the same pointer.
     */
    fun process(action: TouchAction, pointers: List<PointerInfo>, t0Ns: Long? = null) {
        when (action) {
            TouchAction.Down -> {
                // The first finger down resets the state. Any
                // stray pointers from a previous gesture (a
                // missed Cancel, an interrupted sequence) are
                // dropped without a callback: the engine is
                // already neutral.
                if (active.isNotEmpty()) {
                    active.clear()
                }
                for (p in pointers) {
                    require(p.id >= 0) { "Pointer id must be non-negative (got ${p.id})." }
                    active[p.id] = p
                    callback(p.id, p.toTouchPoint(), t0Ns)
                }
            }
            TouchAction.PointerDown -> {
                for (p in pointers) {
                    require(p.id >= 0) { "Pointer id must be non-negative (got ${p.id})." }
                    active[p.id] = p
                    callback(p.id, p.toTouchPoint(), t0Ns)
                }
            }
            TouchAction.Move -> {
                // The `Move` event carries every active
                // pointer's current position. The dispatcher
                // re-emits a callback for each so the engine
                // sees the latest state.
                for (p in pointers) {
                    if (p.id in active) {
                        active[p.id] = p
                        callback(p.id, p.toTouchPoint(), t0Ns)
                    } else {
                        // A `Move` for a pointer we have not
                        // seen go down. Defensive: the
                        // platform should not produce this,
                        // but if it does, treat it as a fresh
                        // `PointerDown` for the same id.
                        active[p.id] = p
                        callback(p.id, p.toTouchPoint(), t0Ns)
                    }
                }
            }
            TouchAction.PointerUp -> {
                for (p in pointers) {
                    active.remove(p.id)
                    callback(p.id, null, t0Ns)
                }
            }
            TouchAction.Up -> {
                // The last finger up. The Android `MotionEvent`
                // carries the released pointer; the dispatcher
                // also flushes any leftover pointers as a
                // safety net.
                val leftover = active.keys.toList()
                for (p in pointers) {
                    active.remove(p.id)
                    callback(p.id, null, t0Ns)
                }
                for (id in leftover) {
                    if (id in active) {
                        active.remove(id)
                        callback(id, null, t0Ns)
                    }
                }
            }
            TouchAction.Cancel -> {
                // Per §11, every cancellation must produce a
                // neutral state. The dispatcher removes every
                // active pointer and emits a `null` callback
                // for each.
                val ids = active.keys.toList()
                active.clear()
                for (id in ids) {
                    callback(id, null, t0Ns)
                }
                // If the Cancel event also lists pointers
                // (some platforms do), make sure their
                // removal is also signalled — though the
                // active set has already been cleared.
                for (p in pointers) {
                    callback(p.id, null, t0Ns)
                }
            }
        }
    }

    /**
     * Force-clear the dispatcher's state. The dispatcher
     * emits a `null` callback for every active pointer.
     * Used by the engine on every state-machine transition
     * out of [EngineState.Active] (per §38) so a
     * transition in flight when a touch gesture is in
     * progress does not leave a stuck pointer on the
     * host.
     */
    fun reset(t0Ns: Long? = null) {
        val ids = active.keys.toList()
        active.clear()
        for (id in ids) {
            callback(id, null, t0Ns)
        }
    }
}
